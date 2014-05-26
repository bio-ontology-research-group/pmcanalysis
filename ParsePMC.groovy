import org.jsoup.*
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
import opennlp.tools.sentdetect.*


def MINLENGTH = 3
def THREADS = 32
def pool = Executors.newFixedThreadPool(THREADS)
def defer = { c -> 
  def f = pool.submit(c as Callable)
}

def foutsentences = new PrintWriter(new BufferedWriter(new FileWriter("results/pmc-sentences")))

TokenizerModel tokenizerModel = new TokenizerModel(new FileInputStream("trainedmodels/en-token.bin"))
Tokenizer tokenizer = new TokenizerME(tokenizerModel)

InputStream modelIn = new FileInputStream("en-sent.bin");

SentenceModel model = null
try {
  model = new SentenceModel(modelIn);
}
catch (IOException e) {
  e.printStackTrace();
} finally {
  if (modelIn != null) {
    try {
      modelIn.close();
    }
    catch (IOException e) {
    }
  }
}
SentenceDetectorME sentenceDetector = new SentenceDetectorME(model)

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
Map<String, Set<String>> name2iddis = [:]
Map<String, Set<String>> name2idpheno = [:]

def parseOntologyFile = { ontfile, map ->
  def id = ""
  ontfile.eachLine { line ->
    if (line.startsWith("id:")) {
      id = line.substring(3).trim()
    }
    if (line.startsWith("name:")) {
      def name = line.substring(5).trim().toLowerCase()
      if (map[name] == null) {
	map[name] = new TreeSet()
      }
      map[name].add(id)
    }
    if (line.startsWith("synonym:")) {
      def syn = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\"")).trim().toLowerCase()
      if (map[syn] == null) {
	map[syn] = new TreeSet()
      }
      map[syn].add(id)
    }
    if (line.startsWith("xref:")) {
      if (line.indexOf("\"")>-1) {
	def syn = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\"")).trim().toLowerCase()
	if (map[syn] == null) {
	  map[syn] = new TreeSet()
	}
	map[syn].add(id)
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
parseOntologyFile(new File("ontologies/HumanDO.obo"), name2iddis)
parseOntologyFile(new File("ontologies/dermo-with-xrefs.obo"), name2iddis)
parseOntologyFile(new File("ontologies/human-phenotype-ontology.obo"), name2idpheno)
parseOntologyFile(new File("ontologies/mammalian_phenotype.obo"), name2idpheno)
new File("ontologies/phenotype_annotation.tab").splitEachLine("\t") { line ->
  if (line[2] && line[5]) {
    def name = line[2].toLowerCase().trim()
    def id = line[0].trim()+":"+line[1].trim()
    id2name[id] = name
    name = name.split(";;").collect { it.trim() }
    name = name.collect {
      if (it ==~ /^.\d\d\d\d\d\d.*/) {
	it = it.substring(it.indexOf(" ")).trim()
	if (it.indexOf(";")>-1) {
	  it = it.substring(0, it.indexOf(";")-1).trim()
	}
	return it
      } else { it }
    }
    name.each { n ->
      if (name2iddis[n] == null) {
	name2iddis[n] = new TreeSet()
      }
      name2iddis[n].add(id)
    }
  }
}

Dictionary dictdis = new Dictionary(false) // diseases
Dictionary dictpheno = new Dictionary(false) // phenotypes
name2iddis.each { tok, ids ->
  //tokens.each { tok ->
  tok = tok.toLowerCase()
  if (tok.length()>MINLENGTH) {
    StringList l = tokenizer.tokenize(tok)
    dictdis.put(l)
  }
}
name2idpheno.each { tok, ids ->
  //tokens.each { tok ->
  tok = tok.toLowerCase()
  if (tok.length()>MINLENGTH) {
    StringList l = tokenizer.tokenize(tok)
    dictpheno.put(l)
  }
}

DictionaryNameFinder finderdis = new DictionaryNameFinder(dictdis)
DictionaryNameFinder finderpheno = new DictionaryNameFinder(dictpheno)

def abstractcounter = 0

def id2numdis = [:]
def id2numpheno = [:]
def idcounterdis = 0
def idcounterpheno = 0
name2iddis.values().flatten().each { id ->
  id2numdis[id] = idcounterdis
  idcounterdis += 1
}
name2idpheno.values().flatten().each { id ->
  id2numpheno[id] = idcounterpheno
  idcounterpheno += 1
}

//ConcurrentMatrix matrix = new ConcurrentMatrix()
DoubleMatrix1D occurrencecountdis = new SparseDoubleMatrix1D(idcounterdis, 5000, 0, 0.95)
DoubleMatrix1D occurrencecountpheno = new SparseDoubleMatrix1D(idcounterpheno, 5000, 0, 0.95)
DoubleMatrix2D cooccurrence = new SparseDoubleMatrix2D(idcounterdis, idcounterpheno, 0, 0, 0.95)

println "Matrix initialized"


XmlSlurper slurper = new XmlSlurper(false, false)
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
new File("pmccorpus").eachDir { dir ->
  dir.eachFile { file ->
    if (file.toString().endsWith("nxml")) {
      println "Processing "+file.toString()+" ($abstractcounter articles processed)"
      abstractcounter += 1
      article = Jsoup.parse(file.getText().replaceAll(">","> ")).text().toLowerCase()
      def sentences = sentenceDetector.sentDetect(article)
      sentences.each { sentence ->
	def tokenizedAbstract = tokenizer.tokenize(sentence)
	def matchesdis = finderdis.find(tokenizedAbstract)
	def occurrencesdis = Span.spansToStrings(matchesdis, tokenizedAbstract)
	occurrencesdis.each { match ->
	  def matchids = name2iddis[match]
	  matchids.each { mid ->
	    if (id2numdis[mid]) {
	      def val = occurrencecountdis.getQuick(id2numdis[mid])
	      val += 1
	      occurrencecountdis.setQuick(id2numdis[mid],val)
	    }
	  }
	}
	def matchespheno = finderpheno.find(tokenizedAbstract)
	def occurrencespheno = Span.spansToStrings(matchespheno, tokenizedAbstract)
	occurrencespheno.each { match ->
	  def matchids = name2idpheno[match]
	  matchids.each { mid ->
	    if (id2numpheno[mid]) {
	      def val = occurrencecountpheno.getQuick(id2numpheno[mid])
	      val += 1
	      occurrencecountpheno.setQuick(id2numpheno[mid],val)
	    }
	  }
	}
	if (matchespheno.length > 0 && matchesdis.length > 0) {
	  occurrencesdis.each { matchstring ->
	    matchids = name2iddis[matchstring]
	    matchids.each { match ->
	      occurrencespheno.each { match2string ->
		matchids2 = name2idpheno[match2string]
		matchids2.each { match2 ->
		  def val = cooccurrence.getQuick(id2numdis[match], id2numpheno[match2])
		  val += 1
		  cooccurrence.setQuick(id2numdis[match], id2numpheno[match2], val)
		  //println "$sentence\t"+matchstring+"\t"+match+"\t"+match2string+"\t"+match2
		}
	      }
	    }
	  }
	}
      }
    }
  }
}
def total = occurrencecountdis.zSum() + occurrencecountpheno.zSum()

def fout = new PrintWriter(new BufferedWriter(new FileWriter("results/pmc-simple")))

id2numdis.each { id, num ->
  def x = occurrencecountdis.getQuick(num)
  def l = [] // npmi
  id2numpheno.each { id2, num2 ->
    def y = occurrencecountpheno.getQuick(num2)
    if (y > 0) {
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
foutsentences.flush()
foutsentences.close()

