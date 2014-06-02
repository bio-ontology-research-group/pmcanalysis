import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.*
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.classic.*
import org.apache.lucene.search.highlight.*
import groovy.json.*

def jsonslurper = new JsonSlurper()

String indexPath = "lucene-medline-pmc/"

def forward(page, req, res){
  def dis = req.getRequestDispatcher(page);
  dis.forward(req, res);
}

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
def owlquerystring = request.getParameter("owlquery")
def ontology = request.getParameter("ontology")?:""
def type = request.getParameter("type")

if (queryString == null && owlquerystring != null) {
  def result = jsonslurper.parse(new URL("http://jagannath.pdn.cam.ac.uk/aber-owl/service/?type=$type&ontology=$ontology&query="+URLEncoder.encode(owlquerystring))) ;
  queryString = ""
  def max = 1023 // only 1024 query terms allows in Lucene
  if (result.size()<max) {
    max = -1
  }
  result[0..max].each { res ->
    if (res.label) {
      queryString += "\"${res.label}\" OR "
    }
  }
}
queryString = queryString.substring(0,queryString.length()-3)

println """
<!doctype html>
<html lang="us">
<head>
	<meta charset="utf-8">
	<title>Aber-OWL: Pubmed - ontology-based access to biomedical literature</title>
	<link href="css/smoothness/jquery-ui-1.10.4.custom.css" rel="stylesheet">
	<script src="js/jquery-1.10.2.js"></script>
	<script src="js/jquery-ui-1.10.4.custom.js"></script>
	<style>
	body{
		font: 100% "Trebuchet MS", sans-serif;
		margin: 80px;
	}
	.menubar {
	  position: fixed;
	  top: 0;
	  left: 0;
	  z-index: 999;
	  width: 95%;
	}
	.demoHeaders {
		margin-top: 2em;
	}
	#icons {
		margin: 0;
		padding: 0;
	}
	#icons li {
		margin: 2px;
		position: relative;
		padding: 4px 0;
		cursor: pointer;
		float: left;
		list-style: none;
	}
	#icons span.ui-icon {
		float: left;
		margin: 0 4px;
	}
	.fakewindowcontain .ui-widget-overlay {
		position: absolute;
	}
	.title {
		text-align: center;
		margin: 0px;
	}
	</style>
</head>
<body>
	<h1 class="title" title="Ontology-based access to biomedical literature">Aber-OWL: Pubmed</h1>

  <br/><br/>

<p>
  <div id="results">
   <ul>
"""


def parser = application.parser
def searcher = application.searcher
def analyzer = application.analyzer
if (queryString) {
  Query query = parser.parse("abstract:($queryString) OR text:($queryString) OR title:($queryString)")
  ScoreDoc[] hits = searcher.search(query, null, 1000, Sort.RELEVANCE, true, true).scoreDocs
  hits.each { doc ->
    Document hitDoc = searcher.doc(doc.doc)
    SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter()
    Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(query))
    highlighter.setTextFragmenter(new NullFragmenter())
    String frag = highlighter.getBestFragment(analyzer, "abstract", hitDoc.get("abstract"))
    if (frag == null) {
      highlighter.setTextFragmenter(new SimpleFragmenter())
      def text = hitDoc.get("text")
      if (text != null) {
	def bestFragment = highlighter.getBestFragments(analyzer, "text", text, 5)
	if (bestFragment) {
	  frag = "<em>No match found in abstract.</em> Full text matches: <ul>"
	  bestFragment.each {
	    frag += "<li>$it</li>\n"
	  }
	  frag += "</ul>"
	}
      } else {
	frag = highlighter.getBestFragment(analyzer, "title", hitDoc.get("title"))
      }
    }
    def pmcid = hitDoc.get("pmcid")
    def pmid = hitDoc.get("pmid")
    def title = hitDoc.get("title")
    if (pmid!=null) {
      println "<li><h3><a href=\"http://www.ncbi.nlm.nih.gov/pubmed/$pmid\">$title</a></h3>"
      if (pmcid!=null) {
	println "<p><a href=\"http://www.ncbi.nlm.nih.gov/pmc/articles/PMC$pmcid\">Full text available.</a></p>"
      }
      println "<p>$frag</p></li>"
    }
  }
}

println """
   </ul>
  </div>

</p>

</body>
</html>
"""
