import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.*
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.classic.*
import org.apache.lucene.search.highlight.*
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.elk.owlapi.*
import groovy.json.*

def fout = new PrintWriter(new BufferedWriter(new FileWriter("drugs2hpo.txt")))

Double npmi(Double total, Double x, Double y, Double xy) {
  Double px = x/total
  Double py = y/total
  Double pxy = xy/total
  Double pmi = Math.log(pxy/(px*py))
  Double npmi = pmi/(-1 * Math.log(pxy))
  return npmi
}

Double tscore(Double total, Double x, Double y, Double xy) {
  return (xy - (x * y / (total * total))) / Math.sqrt(xy)
}

Double zscore(Double total, Double x, Double y, Double xy) {
  return (xy - (x * y / (total * total))) / Math.sqrt(x*y/(total * total))
}

Double lmi(Double total, Double x, Double y, Double xy) {
  return xy * Math.log(total * xy / (x * y))
}

def jsonslurper = new JsonSlurper()

String indexPath = "lucene-medline-pmc/"

Directory dir = FSDirectory.open(new File(indexPath)) 
Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47)

DirectoryReader reader = DirectoryReader.open(dir)
IndexSearcher searcher = new IndexSearcher(reader)

QueryParser parser = new QueryParser(Version.LUCENE_47, "text", analyzer)
QueryBuilder builder = new QueryBuilder(analyzer)

Map<String, Set<String>> id2super = [:]
Map<String, Set<String>> id2sub = [:]
Map<String, Set<String>> name2id = [:].withDefault { new TreeSet() }
Map<String, Set<String>> id2name = [:].withDefault { new TreeSet() }
Map<String, Set<String>> id2pmid = [:].withDefault { new TreeSet() }

def id = ""
def parseOntologies = { filename -> 
  def ontfile = new File(filename)
  ontfile.eachLine { line ->
    if (line.startsWith("id:")) {
      id = line.substring(3).trim()
    }
    if (line.startsWith("name:")) {
      def name = line.substring(5).trim()
      if (name2id[name] == null) {
	name2id[name] = new TreeSet()
      }
      name2id[name].add(id)
      id2name[id].add(name)
    }
    if (line.startsWith("synonym:")) {
      def syn = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\"")).trim()
      if (name2id[syn] == null) {
	name2id[syn] = new TreeSet()
      }
      name2id[syn].add(id)
      id2name[id].add(syn)
  }
    if (line.startsWith("xref:")) {
      if (line.indexOf("\"")>-1) {
	def syn = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\"")).trim()
	if (name2id[syn] == null) {
	  name2id[syn] = new TreeSet()
	}
	name2id[syn].add(id)
	id2name[id].add(syn)
      }
    }
  }
  OWLOntologyManager manager = OWLManager.createOWLOntologyManager()
  
  OWLDataFactory fac = manager.getOWLDataFactory()
  def factory = fac
  
  OWLOntology ont = manager.loadOntologyFromOntologyDocument(ontfile)
  
  OWLReasonerFactory reasonerFactory = null
  
  //  ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor()
  //  OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor)
  
  OWLReasonerFactory f1 = new ElkReasonerFactory()
  OWLReasoner reasoner = f1.createReasoner(ont)
  
  reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)
  
  ont.getClassesInSignature().each { cl ->
    def clst = cl.toString().replaceAll("<http://purl.obolibrary.org/obo/","").replaceAll(">","").replaceAll("_",":")
    if (id2sub[clst] == null) {
      id2sub[clst] = new TreeSet()
    }
    //    id2super[clst].add(clst)
    reasoner.getSubClasses(cl, false).getFlattened().each { sup ->
      def supst = sup.toString().replaceAll("<http://purl.obolibrary.org/obo/","").replaceAll(">","").replaceAll("_",":")
      id2sub[clst].add(supst)
    }
  }
}

parseOntologies("ontologies/mammalian_phenotype.obo")
parseOntologies("ontologies/human-phenotype-ontology.obo")
parseOntologies("ontologies/chebi.obo")


BooleanQuery.setMaxClauseCount(2048)
id2name.each { k, v ->
  Set s = new TreeSet()
  s.add(k)
  //  id2sub[k]?.each { s.add(it) }
  BooleanQuery query = new BooleanQuery()
  s.each { i ->
    id2name[i]?.each { name ->
      try {
	Query q = builder.createBooleanQuery("abstract", "\"$name\"")
	query.add(q, BooleanClause.Occur.SHOULD)
	//	q = builder.createBooleanQuery("title", "\"$name\"")
	//	query.add(q, BooleanClause.Occur.SHOULD)
      } catch (Exception E) {}
    }
  }
  println "Querying $k..."
  ScoreDoc[] hits = null
  try {
    hits = searcher.search(query, null, 1000, Sort.RELEVANCE, true, true).scoreDocs
  } catch (Exception E) {}
  hits?.each { doc ->
    Document hitDoc = searcher.doc(doc.doc)
    def pmid = hitDoc.get("pmid")
    if (pmid) {
      id2pmid[k].add(pmid)
    }
  }
}

println "adding subclasses..."
def id2pmidClosed = [:]
id2pmid.each { k, v ->
  Set s = new TreeSet(v)
  id2sub[k].each { sub ->
    if (sub in id2pmid.keySet()) {
      s.addAll(id2pmid[sub])
    }
  }
  id2pmidClosed[k] = s
}
id2pmid = id2pmidClosed



Set tempSet = new LinkedHashSet()
id2pmid.each { k, v ->
  tempSet.addAll(v)
}
def corpussize = tempSet.size()

println "Corpussize $corpussize..."

println "Indexing PMIDs..."
def indexPMID = [:]
def count = 0
tempSet.each { pmid ->
  indexPMID[pmid] = count
  count += 1
}

println "Generating BitSets..."
Map<String, Set<String>> bsid2pmid = [:]
id2pmid.each { k, v ->
  OpenBitSet bs = new OpenBitSet(corpussize)
  v.each { pmid ->
    bs.set(indexPMID[pmid])
  }
  bsid2pmid[k] = bs
}

bsid2pmid.findAll { k, v -> k.indexOf("CHEBI")>-1 }.each { doid, pmids1 ->
  println "  Computing on $doid..."
  bsid2pmid.findAll { k, v -> (k.indexOf("HP")>-1 || k.indexOf("MP")>-1 ) }.each { pid, pmids2 ->
    def nab = OpenBitSet.intersectionCount(pmids1, pmids2)
    def na = pmids1.cardinality()
    def nb = pmids2.cardinality()
    def tscore = tscore(corpussize, na, nb, nab)
    def pmi = npmi(corpussize, na, nb, nab)
    def zscore = zscore(corpussize, na, nb, nab)
    def lmi = lmi(corpussize, na, nb, nab)
    def name1 = id2name[doid]
    def name2 = id2name[pid]
    if (pmi >= 0.1 && pmi <=1) {
      fout.println("$doid\t$pid\t$tscore\t$zscore\t$lmi\t$pmi\t$nab\t$na\t$nb\t$name1\t$name2")
    }
  }
}

fout.flush()
fout.close()
