import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.*
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.classic.*
import org.apache.lucene.search.highlight.*

String indexPath = "lucene-pmc/"

if (!application) {
  application = request.getApplication(true);
}

if (!application.searcher) {
  Directory dir = FSDirectory.open(new File(indexPath)) 
  Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_47)

  DirectoryReader reader = DirectoryReader.open(dir)
  IndexSearcher searcher = new IndexSearcher(reader)

  QueryParser parser = new QueryParser(Version.LUCENE_47, "text", analyzer)

  application.parser = parser
  application.searcher = searcher
  application.analyzer = analyzer

}

def queryString = request.getParameter("query")
def parser = application.parser
def searcher = application.searcher
def analyzer = application.analyzer
if (queryString) {
  Query query = parser.parse("$queryString")
  ScoreDoc[] hits = searcher.search(query, null, 1000, Sort.RELEVANCE, true, true).scoreDocs
  html.html {
    head {
      title("PMC Query")
    }
    body {
      hits.each { doc ->
	Document hitDoc = searcher.doc(doc.doc)
	SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter()
	Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(query))
	String[] frags = highlighter.getBestFragments(analyzer, "text", hitDoc.get("text"), 5)
	def frag = ""
	frags.each { frag += it+"..." }
	def docid = hitDoc.get("pmcid")
	def title = hitDoc.get("title")
	p {
	  a(href:"http://www.ncbi.nlm.nih.gov/pmc/$docid", title)
	}
	p {
	  println frag
	}
      }
    }
  }
}
