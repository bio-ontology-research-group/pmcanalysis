import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.*
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.classic.*
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
import opennlp.tools.dictionary.*
import opennlp.tools.tokenize.*
import opennlp.tools.util.*
import opennlp.tools.chunker.*
import opennlp.tools.postag.*
import opennlp.tools.namefind.*


def MINLENGTH = 3
def THREADS = 32
def pool = Executors.newFixedThreadPool(THREADS)
def defer = { c -> 
  def f = pool.submit(c as Callable)
}


String indexPath = "lucene-index"

Directory dir = FSDirectory.open(new File(indexPath)) // RAMDirectory()
Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47)
IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_47, analyzer)
iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE)
iwc.setRAMBufferSizeMB(32768.0)
IndexWriter writer = new IndexWriter(dir, iwc)


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

def map = [:].withDefault { new TreeSet() }

def parseOntologyFile = { ontfile ->
  def id = ""
  ontfile.eachLine { line ->
    if (line.startsWith("id:")) {
      id = line.substring(3).trim()
    }
    if (line.startsWith("name:")) {
      def name = line.substring(5).trim().toLowerCase()
      map[name].add(id)
    }
    if (line.startsWith("synonym:")) {
      def syn = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\"")).trim().toLowerCase()
      map[syn].add(id)
    }
    if (line.startsWith("xref:")) {
      if (line.indexOf("\"")>-1) {
	def syn = line.substring(line.indexOf("\"")+1, line.lastIndexOf("\"")).trim().toLowerCase()
	map[syn].add(id)
      }
    }
  }
}

new File("obo/").eachFile { file ->
  parseOntologyFile(file)
}


TokenizerModel tokenizerModel = new TokenizerModel(new FileInputStream("trainedmodels/en-token.bin"))
Tokenizer tokenizer = new TokenizerME(tokenizerModel)
Dictionary dict = new Dictionary(false)
map.each { tok, ids ->
  tok = tok.toLowerCase()
  if (tok.length()>MINLENGTH) {
    StringList l = new StringList(tokenizer.tokenize(tok))
    dict.put(l)
  }
}

DictionaryNameFinder finder = new DictionaryNameFinder(dict)


def counter = 0

new File("medlinecorpus").eachFile { file ->
  if (file.toString().endsWith("xml")) {
    println "Processing "+file.toString()+" ($counter abstracts processed)"
    defer {
      def filename = file.getName()
      def fout = new PrintWriter(new BufferedWriter(new FileWriter("medlineoboresults/$filename")))
      XmlSlurper slurper = new XmlSlurper(false, false)
      slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      abstracts = slurper.parse(file)
      abstracts.MedlineCitation.each { article ->
	finder.clearAdaptiveData()
	counter += 1
	def abstractText = article.Article.Abstract.AbstractText.toString().toLowerCase()
	def pmid = article.PMID.toString()
	fout.print(pmid+"\t")
	def tokenizedAbstract = tokenizer.tokenize(abstractText)
	
	def matches = finder.find(tokenizedAbstract)
	
	def occurrences = Span.spansToStrings(matches, tokenizedAbstract)
	
	def ts = new TreeSet()
	
	occurrences.each { match ->
	  ts.add(match)
	}
	
	ts.each { name ->
	  map[name].each {
	    fout.print(it+"\t")
	  }
	}
	fout.println("")
      }
      fout.flush()
      fout.close()
    }
  }
}

pool.shutdown()
