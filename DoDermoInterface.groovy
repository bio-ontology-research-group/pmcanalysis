import groovy.json.*

if (!application) {
  application = request.getApplication(true);
}

Map dismap = application.dismap
Map dis2name = application.dis2name
Map pheno2name = application.pheno2name
Map name2doid = application.name2doid
Map dermo2mgi = application.dermo2mgi
Map mgi2name = application.mgi2name
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
} else if (rtype == "sim" && query) {

  if (dermo2mgi[query]) {
    def shortList = dermo2mgi[query].sort { it.val }.reverse()
    shortList.each { exp ->
      l << exp
    }
  }
  
} else if (query) {
  def lsize = dismap[query]?.size()
  dismap[query]?.each { exp ->
    /*
    def ts = dismap[query]?.sort { it.tscore }.indexOf(exp) / lsize
    def pmi = dismap[query]?.sort { it.pmi }.indexOf(exp) / lsize
    def zs = dismap[query]?.sort { it.zscore }.indexOf(exp) /lsize
    def lmi = dismap[query]?.sort { it.lmi }.indexOf(exp) /lsize
    exp.ts = ts
    exp.pm = pmi
    exp.zs = zs
    exp.lmi = lmi
    exp.mean = Math.pow(ts * pmi * zs * lmi, 1/4)
    */
    if (exp.chi == Double.NaN) exp.chi = 0
    if (exp.pmi != Double.NaN && exp.lmi!= Double.NaN && exp.mean != Double.NaN && exp.zscore != Double.NaN && exp.tscore != Double.NaN && exp.chi != Double.NaN) {
      l << exp
    }
  }
}
def builder = new JsonBuilder(l)
print builder.toString()
  
