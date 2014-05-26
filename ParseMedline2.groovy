import opennlp.tools.dictionary.*
import opennlp.tools.tokenize.*
import opennlp.tools.util.*
import opennlp.tools.chunker.*
import opennlp.tools.postag.*
import org.apache.commons.io.IOUtils
import opennlp.tools.namefind.*
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.profiles.*
import org.semanticweb.owlapi.util.*
import org.mindswap.pellet.KnowledgeBase
import org.mindswap.pellet.expressivity.*
import org.mindswap.pellet.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.elk.owlapi.*
import java.util.concurrent.*
import cern.colt.matrix.*
import cern.colt.matrix.impl.*


def THREADS = 32
def pool = Executors.newFixedThreadPool(THREADS)
def defer = { c -> 
  def f = pool.submit(c as Callable)
}

TokenizerModel tokenizerModel = new TokenizerModel(new FileInputStream("trainedmodels/en-token.bin"))
Tokenizer tokenizer = new TokenizerME(tokenizerModel)


Double npmi(Double total, Double x, Double y, Double xy) {
  //  Double px = x/total
  Double py = y/total
  Double pxy = xy/total
  //  Double pmi = Math.log(pxy/(px*py))
  Double pmi = Math.log((xy/x)/py)
  Double npmi = pmi/(-1 * Math.log(pxy))
  return npmi
}


String concat(String[] words, int start, int end) {
  StringBuilder sb = new StringBuilder()
  for (int i = start; i < end; i++) {
    sb.append((i > start ? " " : "") + words[i])
  }
  return sb.toString()
}

String[] ngrams(int n, String str) { // all ngrams up to length n
  List<String> ngrams = new ArrayList<String>()
  String[] words = str.split(" ")
  for (int j = 1 ; j <= n ; j++) {
    for (int i = 0; i < words.length - j + 1; i++) {
      ngrams.add(concat(words, i, i+j))
    }
  }
  return ngrams.toArray()
}



def tid = ""
def id2name = [:]
new File("ontologies").eachFile { file ->
  file.eachLine { line ->
    if (line.startsWith("id:")) {
      tid = line.substring(3).trim()
    }
    if (line.startsWith("name:")) {
      id2name[tid] = line.substring(5).trim()
    }
  }
}


Map<String, Set<String>> id2super = [:]
Map<String, Set<String>> name2id = [:]

new File("ontologies").eachFile { ontfile ->
  def id = ""
  ontfile.eachLine { line ->
    if (line.startsWith("id:")) {
      id = line.substring(3).trim()
    }
    if (line.startsWith("name:")) {
      def name = line.substring(5).trim().toLowerCase()
      if (name2id[name] == null) {
	name2id[name] = new TreeSet()
      }
      name2id[name].add(id)
    }
    if (line.startsWith("synonym:")) {
      def syn = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\"")).trim().toLowerCase()
      if (name2id[syn] == null) {
	name2id[syn] = new TreeSet()
      }
      name2id[syn].add(id)
    }
    if (line.startsWith("xref:")) {
      if (line.indexOf("\"")>-1) {
	def syn = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\"")).trim().toLowerCase()
	if (name2id[syn] == null) {
	  name2id[syn] = new TreeSet()
	}
	name2id[syn].add(id)
      }
    }
  }
  
  OWLOntologyManager manager = OWLManager.createOWLOntologyManager()
  
  OWLDataFactory fac = manager.getOWLDataFactory()
  def factory = fac
  
  OWLOntology ont = manager.loadOntologyFromOntologyDocument(ontfile)
  
  OWLReasonerFactory reasonerFactory = null
  
  ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor()
  OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor)
  
  OWLReasonerFactory f1 = new ElkReasonerFactory()
  OWLReasoner reasoner = f1.createReasoner(ont,config)
  
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

def tokens = name2id.keySet()
Dictionary dict = new Dictionary(false)
tokens.each { tok ->
  tok = tok.toLowerCase()
  StringList l = tokenizer.tokenize(tok)
  dict.put(l)
}

DictionaryNameFinder finder = new DictionaryNameFinder(dict)

def abstractcounter = 0

def id2num = [:]
def idcounter = 0
name2id.values().flatten().each { oid ->
  id2num[oid] = idcounter
  idcounter += 1
}

ConcurrentMatrix matrix = new ConcurrentMatrix(id2num.keySet().size())
println "Matrix initialized"

//ChunkerModel chunkModel = new ChunkerModel(new FileInputStream("trainedmodels/en-chunker.bin"))
//ChunkerME chunker = new ChunkerME(chunkModel)

//POSModel posModel = new POSModel(new FileInputStream("trainedmodels/en-pos-maxent.bin"))
//POSTaggerME tagger = new POSTaggerME(posModel)



//def file = new File(args[0])

//args[1..-1].each { filename ->
new File("medlinecorpus").eachFile { file ->
  if (file.toString().endsWith("xml")) {
    println "Processing "+file.toString()+" ($abstractcounter abstracts processed)"
    XmlSlurper slurper = new XmlSlurper(false, false)
    slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    abstracts = slurper.parse(file)
    abstracts.MedlineCitation.Article.each { article ->
      finder.clearAdaptiveData()
      abstractcounter += 1
      def abstractText = article.Abstract.AbstractText.toString().toLowerCase()
	
      def tokenizedAbstract = tokenizer.tokenize(abstractText)

      def matches = finder.find(tokenizedAbstract)

      def occurrences = Span.spansToStrings(matches, tokenizedAbstract)
	
      occurrences.each { match ->
	def matchids = name2id[match]
	matchids.each { mid ->
	  matrix.increment(id2num[mid])
	}
      }
      if (occurrences.size()>1) {
	occurrences.each { matchstring ->
	  matchids = name2id[matchstring]
	  matchids.each { match ->
	    occurrences.each { match2string ->
	      matchids2 = name2id[match2string]
	      matchids2.each { match2 ->
		matrix.increment(id2num[match], id2num[match2])
	      }
	    }
	  }
	}
      }
    }
    //    println cooccurrence
    //      println article.Abstract.AbstractText
    //      println ""
  }
}

def total = matrix.occurrencecount.zSum()

def occurrencecount = matrix.occurrencecount
def cooccurrence = matrix.cooccurrence
def fout = new PrintWriter(new BufferedWriter(new FileWriter("medline-simple")))

id2num.each { id, num ->
  def x = occurrencecount.getQuick(num)
  def l = [] // npmi
  id2num.each { id2, num2 ->
    def y = occurrencecount.getQuick(num2)
    if (y != 0) {
      def xy = cooccurrence.getQuick(num, num2)
      if (xy > 0) {
	def pmi = npmi(total, x, y, xy)
	Expando exp = new Expando()
	exp.name = id2
	exp.npmi = pmi
	exp.count = xy
	exp.x = x
	exp.y = y
	l << exp
      }
    }
  }
  l = l.sort { it.npmi }.reverse()

  l.each { exp ->
    fout.println(id+"\t"+id2name[id]+"\t"+exp.name+"\t"+id2name[exp.name]+"\t"+exp.npmi+"\t"+exp.x+"\t"+exp.y+"\t"+exp.count)
  }
}

fout.flush()
fout.close()


fout = new PrintWriter(new BufferedWriter(new FileWriter("medline-processed")))

id2super.each { id, sup ->
  def num = id2num[id]
  def val = occurrencecount.getQuick(num)
  if (num) {
    sup.each { id2 ->
      def num2 = id2num[id2]
      if (num2) {
	def val2 = occurrencecount.getQuick(num2)
	val2 += val
	occurrencecount.setQuick(num2, val2)

	for (int i = 0 ; i < occurrencecount.size() ; i++) {
	  def v1 = cooccurrence.getQuick(num, i)
	  def v2 = cooccurrence.getQuick(num2, i)
	  v2 += v1
	  cooccurrence.setQuick(num2, i, v2)
	  v1 = cooccurrence.getQuick(i, num)
	  v2 = cooccurrence.getQuick(i, num2)
	  v2 += v1
	  cooccurrence.setQuick(i, num2, v2)
	}
      }
    }
  }
}

id2num.each { id, num ->
  def x = occurrencecount.getQuick(num)
  def l = [] // npmi
  id2num.each { id2, num2 ->
    def y = occurrencecount.getQuick(num2)
    if (y != 0) {
      def xy = cooccurrence.getQuick(num, num2)
      if (xy > 0) {
	def pmi = npmi(total, x, y, xy)
	Expando exp = new Expando()
	exp.name = id2
	exp.npmi = pmi
	exp.count = xy
	exp.x = x
	exp.y = y
	l << exp
      }
    }
  }
  l = l.sort { it.npmi }.reverse()

  l.each { exp ->
    fout.println(id+"\t"+id2name[id]+"\t"+exp.name+"\t"+id2name[exp.name]+"\t"+exp.npmi+"\t"+exp.x+"\t"+exp.y+"\t"+exp.count)
    //    fout.println(id+"\t"+exp.name+"\t"+exp.npmi+"\t"+exp.count)
  }
}
fout.flush()
fout.close()


System.exit(-1)
