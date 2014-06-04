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

def textquerystring = request.getParameter("query")
def owlquerystring = request.getParameter("owlquery")
def ontology = request.getParameter("ontology")?:""
def output = request.getParameter("output")?:""
def type = request.getParameter("type")
def queryString = null

if (owlquerystring != null) {
  def result = jsonslurper.parse(new URL("http://jagannath.pdn.cam.ac.uk/aber-owl/service/?type=$type&ontology=$ontology&query="+URLEncoder.encode(owlquerystring))) ;
  queryString = ""
  def max = 1023 // only 1024 query terms allows in Lucene
  if (result.size()<max) {
    max = -1
  }
  if (result.size()==0) { 
    queryString = "" 
  } else {
    result[0..max].each { res ->
      if (res.label) {
	queryString += "\"${res.label}\" OR "
      }
    }
  }
}
if (queryString.length()>3) {
  queryString = queryString.substring(0,queryString.length()-3)
}
if (textquerystring == null) {
  textquerystring = ""
}

if (output==null || output=="text") { // text output
println """<!doctype html>
<html lang="us">
<head>
	<meta charset="utf-8">
	<title>Aber-OWL: Pubmed - ontology-based access to biomedical literature</title>
	<link href="../css/smoothness/jquery-ui-1.10.4.custom.css" rel="stylesheet">
	<script src="../js/jquery-1.10.2.js"></script>
	<script src="../js/jquery-ui-1.10.4.custom.js"></script>

        <script>
	    \$( "#radioset" ).buttonset();

        \$(document).ready(function() {

        \$('#search').keyup(function(event) {
        var search_text = \$('#search').val(); 
        var rg = new RegExp(search_text,'i');
        \$('#resultlist .abstract').each(function(){ 
             if(\$.trim(\$(this).html()).search(rg) == -1) {
             \$(this).parent().css('display', 'none');
             \$(this).css('display', 'none');
             \$(this).next().css('display', 'none');
        }
         else {
             \$(this).parent().css('display', '');
             \$(this).css('display', '');
             \$(this).next().css('display', '');
        }
       });
});

         \$.ajax({
	  url:'../service/',
	  type:'GET',
	  data: 'type=listontologies',
	  dataType: 'json',
	  success: function( json ) {
            \$.each(json, function(i, value) {
              \$('#ontologyselector').append(\$('<option>').text(value).attr('value', value));
            
               });
	    }
         });
        });
</script>
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
	.list {
	  font-family:sans-serif;
	  margin:0;
	  padding:20px 0 0;
	}
	.list > li {
	  display:block;
	  background-color: #eee;
	  padding:10px;
	  box-shadow: inset 0 1px 0 #fff;
	}
	.list > h3 {
	  font-size: 16px;
	  margin:0 0 0.3rem;
	  font-weight: normal;
	  font-weight:bold;
	}
	input {
	  border:solid 1px #ccc;
	  border-radius: 5px;
	  padding:7px 14px;
	  margin-bottom:10px
	}
	input:focus {
	  outline:none;
	  border-color:#aaa;
	}
        #search {
          float:right;
          overflow: hidden;
          position: relative;
        }

	</style>
</head>
<body>
	<h1 class="title" title="Ontology-based access to biomedical literature">Aber-OWL: Pubmed</h1>

<div id='searchbar'>
  <form style="margin-top: 1em;" >
    <div>
      <center>
"""

print '<input type="radio" id="radio1" name="type" value="superclass"'
if (type=="superclass") print ' checked="checked"'
println '><label for="radio1">Superclasses</label>'
print '<input type="radio" id="radio1" name="type" value="equivalent"'
if (type=="equivalent") print ' checked="checked"'
println '><label for="radio2">Equivalent classes</label>'
print '<input type="radio" id="radio1" name="type" value="subclass"'
if (type=="subclass") print ' checked="checked"'
println '><label for="radio1">Subclasses</label>'


println """
      </center>
      <center>
	<select id="ontologyselector" name="ontologyselector">
	  <option selected="selected" value=""></option>
	</select>

      </center>
    </div>

    <center> <input size=100% title=\"OWL query\" type='text' name='owlquery' value=\"$owlquerystring\" />
<br><br>
    <center>
      <input type=submit id="button" value="Submit">
</input>

</center>

</form>
</div>

  <br/><br/>

<p>
  <div id="resultlist" class="resultlist">
   <ul class="list">
    <input name="" type="text"  id="search" autocomplete="off" class="search-box-bg" />
<br><br>

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
      println "<li><h3 class=\"title\"><a href=\"http://www.ncbi.nlm.nih.gov/pubmed/$pmid\">$title</a></h3>"
      if (pmcid!=null) {
	println "<p><a href=\"http://www.ncbi.nlm.nih.gov/pmc/articles/PMC$pmcid\">Full text available.</a></p>"
      }
      println "<p class=\"abstract\">$frag</p></li>"
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
} else if (output == "json") {
  List l = []
  def parser = application.parser
  def searcher = application.searcher
  def analyzer = application.analyzer
  if (queryString) {
    Query query = parser.parse("abstract:($queryString) OR text:($queryString) OR title:($queryString)")
    ScoreDoc[] hits = searcher.search(query, null, 1000, Sort.RELEVANCE, true, true).scoreDocs
    hits.each { doc ->
      Document hitDoc = searcher.doc(doc.doc)
      l << [ pmcid: hitDoc.get("pmcid"), pmid:hitDoc.get("pmid"), title:hitDoc.get("title") ]
    }
  }
  def builder = new JsonBuilder(l)
  print builder.toString()
}