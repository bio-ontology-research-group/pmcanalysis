import groovy.json.*

if (!application) {
  application = request.getApplication(true);
}

Map dismap = application.dismap
Map dis2name = application.dis2name
Map pheno2name = application.pheno2name
Map name2doid = application.name2doid
SuggestTree donames = application.donames

def rtype = request.getParameter("type")
def query = request.getParameter("q")

List l = []
if (rtype == "names" && query) {
  SuggestTree.Node n = donames.autocompleteSuggestionsFor(query.toLowerCase())
  if (n != null) {
    for (int i = 0 ; i < n.listLength() ; i++) {
      String elem = n.listElement(i).trim()
      if (elem.trim().length()>0) {
	try {
	  def id = elem.substring(elem.lastIndexOf("(")+1, elem.length()-1)
	  def name = elem.substring(0, elem.lastIndexOf("("))
	  Expando exp = new Expando()
	  exp.label = name
	  exp.id = id
	  l << exp
	} catch (Exception E) {}
      }
    }
  }
} else if (query) {
  dismap[query]?.each { exp ->
    l << exp
  }
}
def builder = new JsonBuilder(l)
print builder.toString()
  
