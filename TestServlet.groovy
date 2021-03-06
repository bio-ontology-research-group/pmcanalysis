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

def parser = application.parser
def searcher = application.searcher
def analyzer = application.analyzer
QueryBuilder builder = new QueryBuilder(analyzer)

def textquerystring = request.getParameter("query")
def owlquerystring = request.getParameterValues("owlquery")
def ontology = request.getParameter("ontology")?:""
def output = request.getParameter("output")
def type = request.getParameter("type")

//def queryString = null

BooleanQuery.setMaxClauseCount(3072)

BooleanQuery luceneQuery = new BooleanQuery()
if (owlquerystring != null) {
  //  queryString = ""
  owlquerystring.each { oqs ->
    BooleanQuery singleQ = new BooleanQuery()
    def result = jsonslurper.parse(new URL("http://aber-owl.net/aber-owl/service/api/runQuery.groovy?type=$type&ontology=$ontology&query="+URLEncoder.encode(oqs)))
    result = result['result']
    def max = 1023
    if (result.size()<max) {
      max = -1
    }
    if (result.size()==0) { 
      //      queryString = "" 
    } else {
      result[0..max].each { res ->
	if (res.label!=null) {
	  Query q = builder.createPhraseQuery("text", "${res.label}")
	  singleQ.add(q, BooleanClause.Occur.SHOULD)
	  q = builder.createPhraseQuery("abstract", "${res.label}")
	  singleQ.add(q, BooleanClause.Occur.SHOULD)
	  q = builder.createPhraseQuery("title", "${res.label}")
	  singleQ.add(q, BooleanClause.Occur.SHOULD)
	  //	if (res.label) {
	  //	  queryString += "\"${res.label}\" OR "
	  //	}
	}
      }
      try {
	luceneQuery.add(singleQ, BooleanClause.Occur.MUST)
      } catch (Exception E) {}
    }
  }
}
//println luceneQuery
//if (queryString.length()>3) {
//  queryString = queryString.substring(0,queryString.length()-3)
//}
if (textquerystring == null) {
  textquerystring = ""
}


if (output==null) { // text output
println """<!doctype html>
<html lang="us">
<head>
	<meta charset="utf-8">
	<title>Aber-OWL: Pubmed - ontology-based access to biomedical literature</title>
<script>
  (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  })(window,document,'script','//www.google-analytics.com/analytics.js','ga');

  ga('create', 'UA-59233722-1', 'auto');
  ga('send', 'pageview');

</script>
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
	  url:'../service/api/listOntologies.groovy',
	  type:'GET',
	  data: 'type=listontologies',
	  dataType: 'json',
	  success: function( json ) {
            \$.each(json, function(i, value) {
              if (value == "$ontology") {
                \$('#ontology').append(\$('<option selected="selected">').text(value).attr('value', value));
              } else {
                \$('#ontology').append(\$('<option>').text(value).attr('value', value));
              }            
               });
	    }
         });
        });



    function split( val ) {
      return val.split( /\\s/ );
    }
    function extractLast( term ) {
      return split( term ).pop();
    }
\$(function() {
		
	
		
            \$( "#button" ).button();
	    \$( "#radioset" ).buttonset();

            \$( ".ac" )
              .on("keydown.autocomplete", "input", function() {
              \$(this)
		.bind( "keydown", function( event ) {
		    	if ( event.keyCode === \$.ui.keyCode.TAB &&
            		\$( this ).data( "ui-autocomplete" ).menu.active ) {
          		event.preventDefault();
        		}
      		})
		.autocomplete({
		    minLength: 3,
		    source: function( request, response ) {
			var ontology = \$( "#ontology option:selected" ).text();
			\$.getJSON( "../service/api/queryNames.groovy", {
			    term: extractLast( request.term ),
			    ontology : ontology,
			}, response );
		    },
		    search: function() {
			// custom minLength
			var term = extractLast( this.value );
			if ( term.length < 3 ) {
			    return false;
			}
		    },
		    focus: function() {
			// prevent value inserted on focus
			return false;
		    },
		    select: function( event, ui ) {
			var terms = split( this.value );
			// remove the current input
			terms.pop();
			// add the selected item
			terms.push( ui.item.value );
			// add placeholder to get the comma-and-space at the end
			terms.push( "" );
			this.value = terms.join( " " );
			return false;
		    }
		})
	.data( "ui-autocomplete" )._renderItem = function( ul, item ) {
	    return \$( "<li>" )
		.append( "<a>" + item.label +"</a>" )
		.appendTo( ul );
	}});
		
		
		
	});


function addBox() {
    var newTextBoxDiv = \$(document.createElement("div")).attr("class", "ac"); //\$("#autocomplete").clone().attr("id", "autocomplete-new").val("").appendTo("#TextBoxesGroup");
    newTextBoxDiv.after().html('<center> <input id="autocomplete-new" size=100% title="OWL query" type="text" name="owlquery" value=""/><button onclick="addBox(); return false;">+</button><a href="../"><img style="height:20px;vertical-align:middle" title="Show in Aber-OWL" src="../img/logo-owl.png" /></center>');
    newTextBoxDiv.appendTo("#TextBoxesGroup");
            \$( ".ac" )
              .on("keydown.autocomplete", "input", function() {
              \$(this)
		.bind( "keydown", function( event ) {
		    	if ( event.keyCode === \$.ui.keyCode.TAB &&
            		\$( this ).data( "ui-autocomplete" ).menu.active ) {
          		event.preventDefault();
        		}
      		})
		.autocomplete({
		    minLength: 3,
		    source: function( request, response ) {
			var ontology = \$( "#ontology option:selected" ).text();
			\$.getJSON( "../service/", {
			    term: extractLast( request.term ),
			    ontology : ontology,
			}, response );
		    },
		    search: function() {
			// custom minLength
			var term = extractLast( this.value );
			if ( term.length < 3 ) {
			    return false;
			}
		    },
		    focus: function() {
			// prevent value inserted on focus
			return false;
		    },
		    select: function( event, ui ) {
			var terms = split( this.value );
			// remove the current input
			terms.pop();
			// add the selected item
			terms.push( ui.item.value );
			// add placeholder to get the comma-and-space at the end
			terms.push( "" );
			this.value = terms.join( " " );
			return false;
		    }
		})
	.data( "ui-autocomplete" )._renderItem = function( ul, item ) {
	    return \$( "<li>" )
		.append( "<a>" + item.label +"</a>" )
		.appendTo( ul );
	}});

    return false ;
}

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
	.menubarleft {
	  position: fixed;
	  top: 0;
	  left: 20px;
	  z-index: 999;
	  width: 95%;
	  display: none;
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
	.articletitle {
		text-align: left;
		margin: 0px;
	}
	.list {
	  font-family:sans-serif;
	  padding:20px 0 0;
	}
	.list > li {
	  font: 80% "Trebuchet MS", sans-serif;
	  display:block;
	  background-color: #eee;
	  padding:10px;
	  box-shadow: inset 0 1px 0 #fff;
	}
	.list > li > h3 {
	  font: 100% "Trebuchet MS", sans-serif;
	  font-size: 16px;
	  margin:0 0 0.3rem;
	  font-weight: normal;
	  font-weight:bold;
	}
	.list > li > article {
	  font: 80% "Trebuchet MS", sans-serif;
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
	.footer {

	   bottom:0;
	   left:100px;
	   width:85%;
	   height:60px;
	   display: flex;
	   justify-content: space-between;

	}

	</style>
</head>
<body>
	<p class="menubar" align="right"><small><a href="../help.html">Help</a></small></p>
	<h1 class="title" title="Ontology-based access to biomedical literature">Aber-OWL: Pubmed</h1>

<div id='searchbar'>
  <form style="margin-top: 1em;" >
    <div>
      <center>
"""

print '<input type="radio" id="radio0" name="type" value="supeq"'
if (type == "supeq") print ' checked="checked"'
print '><label for="radio0">Super- and Equivalent classes</label>'
print '<input type="radio" id="radio1" name="type" value="superclass"'
if (type=="superclass") print ' checked="checked"'
println '><label for="radio1">Superclasses</label>'
print '<input type="radio" id="radio1" name="type" value="equivalent"'
if (type=="equivalent") print ' checked="checked"'
println '><label for="radio2">Equivalent classes</label>'
print '<input type="radio" id="radio1" name="type" value="subclass"'
if (type=="subclass") print ' checked="checked"'
println '><label for="radio1">Subclasses</label>'
print '<input type="radio" id="radio4" name="type" value="subeq"'
if (type == "subeq") print ' checked="checked"'
print '><label for="radio4">Sub- and Equivalent classes</label>'


println """
      </center>
      <center>
	<select id="ontology" name="ontology">
	  <option value=""></option>
	</select>

      </center>
    </div>

<div id='TextBoxesGroup'>
"""
if (owlquerystring == null || owlquerystring.size() == 0) {
  println """
<div class="ac">
    <center><input id="autocomplete" size=100% title=\"OWL query\" type='text' name='owlquery' value="" /><button onclick="addBox(); return false;">+</button>
<a href="../"><img style="height:20px;vertical-align:middle" title="Show in Aber-OWL" src="../img/logo-owl.png" /></center>
</div>
"""
} else {
  def first = true
  owlquerystring.each { oq ->
    if (first) {
      first = false
      println """
<div class="ac">
    <center> <input id="autocomplete" size=100% title=\"OWL query\" type='text' name='owlquery' value=\"$oq\" /><button onclick="addBox(); return false;">+</button>
<a href="../#$oq"><img style="height:20px;vertical-align:middle" title="Show in Aber-OWL" src="../img/logo-owl.png" /></center>

</div>
"""
    } else {
      println """
<div class="ac">
    <center> <input id="autocomplete-new" size=100% title=\"OWL query\" type='text' name='owlquery' value=\"$oq\" /><button onclick="addBox(); return false;">+</button>
<a href="../#$oq"><img style="height:20px;vertical-align:middle" title="Show in Aber-OWL" src="../img/logo-owl.png" /></center>
</div>
"""

    }
  }
}

println """
</div>
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

if (luceneQuery) {
  //  Query query = parser.parse("abstract:($queryString) OR text:($queryString) OR title:($queryString)")
  ScoreDoc[] hits = searcher.search(luceneQuery, null, 10000, Sort.RELEVANCE, true, true).scoreDocs
  def numhits = hits.size()
  if (owlquerystring) {
    if (numhits == 10000) {
      println "$numhits (maximum) matching documents"
    } else {
      println "$numhits matching documents"
    }
  }
  hits.each { doc ->
    Document hitDoc = searcher.doc(doc.doc)
    SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter()
    Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(luceneQuery))
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
    if (frag == null) { // fallback
      frag = hitDoc.get("abstract")
    }
    def pmcid = hitDoc.get("pmcid")
    def pmid = hitDoc.get("pmid")
    def title = hitDoc.get("title")
    if (pmid!=null) {
      println "<li><h3 class=\"articletitle\"><a href=\"http://www.ncbi.nlm.nih.gov/pubmed/$pmid\">$title</a></h3>"
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
<p class="footer">
<img src="../img/aber.gif" alt="Aberystwyth University" height=50 />
<img src="../img/kaust.svg" alt="King Abdullah University of Science and
			      Technology" height=50 />
<img src="../img/cam.svg" alt="University of Cambridge" height=50 />
</p>


  </body>
</html>
"""
} else if (output == "json") {
  List l = []
  if (luceneQuery) {
    //    Query query = parser.parse("abstract:($queryString) OR text:($queryString) OR title:($queryString)")
    ScoreDoc[] hits = searcher.search(luceneQuery, null, 1000, Sort.RELEVANCE, true, true).scoreDocs
    hits.each { doc ->
      Document hitDoc = searcher.doc(doc.doc)
      SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter()
      Highlighter highlighter = new Highlighter(htmlFormatter, new QueryScorer(luceneQuery))
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
      l << [ pmcid: hitDoc.get("pmcid"), pmid:hitDoc.get("pmid"), title:hitDoc.get("title"), fragment:frag ]
    }
  }
  def jsonbuilder = new JsonBuilder(l)
  print jsonbuilder.toString()
}
