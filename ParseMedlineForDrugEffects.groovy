import opennlp.tools.sentdetect.*
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

def MINLENGTH = 3
def THREADS = 32
def pool = Executors.newFixedThreadPool(THREADS)
def defer = { c -> 
  def f = pool.submit(c as Callable)
}

def foutsentences = new PrintWriter(new BufferedWriter(new FileWriter("results/medline-sentences")))

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
Map<String, Set<String>> id2sub = [:]
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
    if (id2sub[clst] == null) {
      id2sub[clst] = new TreeSet()
    }
    //    id2super[clst].add(clst)
    reasoner.getSuperClasses(cl, false).getFlattened().each { sup ->
      def supst = sup.toString().replaceAll("<http://purl.obolibrary.org/obo/","").replaceAll(">","").replaceAll("_",":")
      id2super[clst].add(supst)
    }
    reasoner.getSubClasses(cl, false).getFlattened().each { sup ->
      def supst = sup.toString().replaceAll("<http://purl.obolibrary.org/obo/","").replaceAll(">","").replaceAll("_",":")
      id2sub[clst].add(supst)
    }
  }
}
//parseOntologyFile(new File("ontologies/HumanDO.obo"), name2iddis)
//parseOntologyFile(new File("ontologies/dermo-with-xrefs.obo"), name2iddis)
parseOntologyFile(new File("ontologies/human-phenotype-ontology.obo"), name2idpheno)
parseOntologyFile(new File("ontologies/mammalian_phenotype.obo"), name2idpheno)

new File("../drugeffects/stitch/chemical.aliases.v3.1.tsv").splitEachLine("\t") { line ->
  def n = line[1].toLowerCase()
  def source = line[2]
  if (source in ["ChEMBL_ChemIDplus_plus", "BIDD_ChEBI_plus", "BIDD_BIND_plus"]) {
    def id = line[0].replaceAll("CID","STITCHTM:")
    if (name2iddis[n] == null) {
      name2iddis[n] = new TreeSet()
    }
    name2iddis[n].add(id)
  }
}

new File("../drugeffects/label_mapping.tsv").splitEachLine("\t") { line ->
  def name1 = line[0]
  def name2 = line[1]
  def id = line[3]?.replaceAll("-","STITCHTM:")
  if (id) {
    if (name1) {
      if (name2iddis[name1] == null) {
	name2iddis[name1] = new TreeSet()
      }
      name2iddis[name1].add(id)
    }
    if (name2) {
      if (name2iddis[name2] == null) {
	name2iddis[name2] = new TreeSet()
      }
      name2iddis[name2].add(id)
    }
  }
}

println "Building dictionaries"
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
println "Dictionaries completed."

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
      finderdis.clearAdaptiveData()
      finderpheno.clearAdaptiveData()
      abstractcounter += 1
      def abstractText = article.Abstract.AbstractText.toString()
      def sentences = sentenceDetector.sentDetect(abstractText)
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
		  foutsentences.println("$sentence\t"+matchstring+"\t"+match+"\t"+match2string+"\t"+match2)
		}
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

def total = occurrencecountdis.zSum() + occurrencecountpheno.zSum()

def fout = new PrintWriter(new BufferedWriter(new FileWriter("results/medline-drugs-full-simple")))

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


fout = new PrintWriter(new BufferedWriter(new FileWriter("results/medline-drugs-full-processed")))

/* close the occurrences against ontology hierarchy */
/*
id2numdis.each { dis, num ->
  def v1 = occurrencecountdis.getQuick(num)
  id2super[dis].each { sup ->
    def num2 = id2numdis[sup]
    if (num2) {
      def v2 = occurrencecountdis.getQuick(num2)
      v2 += v1
      occurrencecountdis.setQuick(num2, v2)
    }
  }
}
*/
id2numpheno.each { dis, num ->
  def v1 = occurrencecountpheno.getQuick(num)
  id2super[dis].each { sup ->
    def num2 = id2numpheno[sup]
    if (num2) {
      def v2 = occurrencecountpheno.getQuick(num2)
      v2 += v1
      occurrencecountpheno.setQuick(num2, v2)
    }
  }
}

id2numdis.each { dis, num1 ->
  def dissuper = id2super[dis]
  /* first, close (disease) rows against (phenotype) superclass counts */
  id2numpheno.each { pheno, num2 ->
    def val = cooccurrence.getQuick(num1, num2)
    def phenosuper = id2super[dis]
    phenosuper.each { ps ->
      def numsup = id2numpheno[ps]
      if (numsup != num2 && num1 && numsup) {
	def val2 = cooccurrence.getQuick(num1, numsup)
	val2 += val1
	cooccurrence.setQuick(num1, numsup, val2)
      }
    }
  }
  /* second, close the columns against (disease) superclasses */
  dissuper.each { dis2 ->
    def num2 = id2numdis[dis2]
    if (num2) {
      if (num1 != num2) {
	for (int i = 0 ; i < occurrencecountpheno.size() ; i++) {
	  v1 = cooccurrence.getQuick(num1, i) // num1 is the disease row, i is the phenotype
	  v2 = cooccurrence.getQuick(num2, i) // num2 iterates through all other diseases
	  v2 += v1
	  cooccurrence.setQuick(num2, i, v2)
	}
      }
    }
  }
}

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

System.exit(-1)
