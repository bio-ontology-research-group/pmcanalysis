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

def fout = new PrintWriter(new BufferedWriter(new FileWriter("doid2hpo.txt")))

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

Map<String, Set<String>> id2super = [:]
Map<String, Set<String>> name2id = [:].withDefault { new TreeSet() }
Map<String, Set<String>> id2name = [:].withDefault { new TreeSet() }


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
    if (id2super[clst] == null) {
      id2super[clst] = new TreeSet()
    }
    //    id2super[clst].add(clst)
    reasoner.getSuperClasses(cl, false).getFlattened().each { sup ->
      def supst = sup.toString().replaceAll("<http://purl.obolibrary.org/obo/","").replaceAll(">","").replaceAll("_",":")
      id2super[clst].add(supst)
    }
  }
}

parseOntologies("ontologies/mammalian_phenotype.obo")
parseOntologies("ontologies/human-phenotype-ontology.obo")
parseOntologies("ontologies/HumanDO.obo")


//name2id.findAll { k, v -> v.findAll { it.indexOf("DOID")>-1 }.size()>0 }.each { name1, doids ->
//  name2id.findAll { k, v -> v.findAll { it.indexOf("HP")>-1 }.size()>0 }.each { name2, hpids ->

id2name.findAll { k, v -> k.indexOf("DOID")>-1 }.each { doid, names1 ->
  def name1 = ""
  if (names1.size() == 1) {
    name1 = "\""+names1.first()+"\""
  } else {
    names1.each { name1 += "\"$it\" OR " }
    name1 = name1.substring(0, name1.length() -3 )
  }
  Query query = parser.parse("abstract:($name1) OR title:($name1)") // OR text:($name1)")
  ScoreDoc[] hits = searcher.search(query, null, 1000, Sort.RELEVANCE, true, true).scoreDocs
  def disnum = hits.size()
  if (disnum > 0) {
    id2name.findAll { k, v -> (k.indexOf("HP")>-1 || k.indexOf("MP")>-1 ) }.each { hpid, names2 ->
      def name2 = ""
      if (names2.size() == 1) {
	name2 = "\""+names2.first()+"\""
      } else {
	names2.each { name2 += "\"$it\" OR " }
	name2 = name2.substring(0, name2.length() -3 )
      }
      
      
      query = parser.parse("abstract:($name2) OR title:($name2)") // OR text:($name2)")
      hits = searcher.search(query, null, 1000, Sort.RELEVANCE, true, true).scoreDocs
      def phenonum = hits.size()
      
      query = parser.parse("abstract:(($name2) AND ($name1)) OR title:(($name2) AND ($name1))") // OR text:(($name2) AND ($name1))")
      hits = searcher.search(query, null, 1000, Sort.RELEVANCE, true, true).scoreDocs
      def both = hits.size()
      
      def tscore = tscore(20000000, disnum, phenonum, both)
      def pmi = npmi(20000000, disnum, phenonum, both)
      def zscore = zscore(20000000, disnum, phenonum, both)
      def lmi = lmi(20000000, disnum, phenonum, both)
      fout.println("$doid\t$hpid\t$tscore\t$zscore\t$lmi\t$pmi\t$names1\t$names2")
    }
  }
}									 
									 
fout.flush()
fout.close()
