import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.*
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.classic.*

PrintWriter fout = new PrintWriter(new BufferedWriter(new FileWriter("pmc-tagged.txt")))

String indexPath = "lucene-pmc/"

Directory dir = FSDirectory.open(new File(indexPath)) 
Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47)

DirectoryReader reader = DirectoryReader.open(dir)
IndexSearcher searcher = new IndexSearcher(reader)

QueryParser parser = new QueryParser(Version.LUCENE_47, "text", analyzer)

def map = [:]

def parseOntologyFile = { ontfile ->
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
}

new File("obo/").eachFile { file ->
  parseOntologyFile(file)
}

Map<String, Set<String>> results = [:].withDefault { new LinkedHashSet<String>() } // pmcid -> Set of ontology term ids

map.each { name, ids ->
  println name
  try {
    Query query = parser.parse("\"$name\" OR title:\"$name\" OR abstract:\"$name\"")
    ScoreDoc[] hits = searcher.search(query, null, 1000, Sort.RELEVANCE, true, true).scoreDocs
    hits.each { doc ->
      Document hitDoc = searcher.doc(doc.doc)
      def docid = hitDoc.get("pmcid")
      ids.each { id ->
	results[docid].add(id)
	//            println "$docid\t$name\t$id"
      }
    }
  } catch (Exception E) {}
}
results.each { pmcid, terms ->
  fout.print("$pmcid\t")
  terms.each { fout.print("$it\t") }
  fout.println("") 
}
fout.flush()
fout.close()