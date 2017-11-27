@Grapes([
	  @Grab('org.eclipse.jetty:jetty-server:9.0.0.M5'),
	  @Grab('org.eclipse.jetty:jetty-servlet:9.0.0.M5'),
	  @Grab('javax.servlet:javax.servlet-api:3.0.1'),
	  @GrabExclude('org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016'),
	  @GrabConfig(systemClassLoader=true)
	])
@Grab(group='javax.el', module='javax.el-api', version='3.0.0')

import groovy.json.*
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import groovy.servlet.*
import javax.servlet.http.*
import javax.servlet.ServletConfig

class DoDermoServer extends HttpServlet {
  public final static String disfilename = "filtered-dermo-definitions-abstracts/filtered-doid-pheno-0.05.txt"
  public final static String simfilename = "mgi-similarity/dermo-mgi-0.05.txt"
  public final static String mgiNameFilename = "mgi-similarity/mousephenotypes-names.txt"
  public final static maxSize = 100
  def requestHandler
  def context
  def id2name = [:]
  def dermo2mgi = [:].withDefault { new PriorityQueue(maxSize+1, [compare:{a,b -> a.val == b.val ? a.mgi.compareTo(b.mgi) : a.val.compareTo(b.val)}] as Comparator) }
  def mgi2name = [:]
  void init(ServletConfig config) {
    super.init(config)
    context = config.servletContext
  }

  public DoDermoServer() {
    println "Reading names..."
    new File(mgiNameFilename).splitEachLine("\t") { line ->
      def id = line[0]
      def name = line[1]
      mgi2name[id] = name
    }
    println "Reading similarity file..."
    new File(simfilename).splitEachLine("\t") { line ->
      def did = line[0]
      def mgiid = line[1].replaceAll("http://phenomebrowser.net/smltest/","")
      def val = new Double(line[2])
      Expando exp = new Expando()
      exp.mgi = mgiid
      exp.val = val
      exp.name = mgi2name[mgiid]
      dermo2mgi[did].add(exp)
      if (dermo2mgi[did].size() > maxSize) { 
	dermo2mgi[did].poll()
      }
    }
    def id = ""
    def readNames = { fn -> 
      new File(fn).eachLine { line ->
	if (line.startsWith("id:")) {
	  id = line.substring(3).trim()
	}
	if (line.startsWith("name:")) {
	  id2name[id] = line.substring(5).trim()
	}
      }
    }
    readNames("ontologies/HumanDO.obo")
    readNames("ontologies/human-phenotype-ontology.obo")
    readNames("ontologies/mammalian_phenotype.obo")
  }

  void service(HttpServletRequest request, HttpServletResponse response) {
    requestHandler.binding = new ServletBinding(request, response, context)
    use (ServletCategory) {
      requestHandler.call()
    }
  }
  void run(int port) {
    def jetty = new Server(port)
    def context = new ServletContextHandler(jetty, '/', ServletContextHandler.SESSIONS)
    context.resourceBase = '.'  
    context.addServlet(GroovyServlet, '/DoDermoInterface.groovy')
    context.setAttribute('version', '1.0')  
    SuggestTree donames = new SuggestTree(500, new HashMap<String, Integer>());

    Map dismap = [:].withDefault { new LinkedHashSet() }
    Map dis2name = [:].withDefault { new TreeSet() }
    Map name2doid = [:].withDefault { new TreeSet() }
    Map pheno2name = [:].withDefault { new TreeSet() }
    println "Reading file..."
    new File(disfilename).splitEachLine("\t") { line ->
      try {
	def doid = line[0]
	Expando exp = new Expando()
	exp.mp = line[1]
	exp.tscore = new Double(line[2])
	exp.zscore = new Double(line[3])
	exp.lmi = new Double(line[4])
	exp.pmi = new Double(line[5])
	exp.chi = new Double(line[6])
	exp.mean = new Double(line[7])
	dismap[doid].add(exp)
	exp.mpname = id2name[exp.mp]
	def dname = line[8].trim()
	dname = dname.substring(1, dname.length()-1)
	dname.split(",").each { 
	  it = it.trim()
	  if (it.length()>0) {
	    dis2name[doid].add(it)
	    name2doid[it].add(doid)
	    try {
	      donames.insert(it.toLowerCase()+" ($doid)", 10000 - it.length())
	    } catch (IllegalStateException E) {}
	  }
	}
      } catch (Exception E) {
	//	E.printStackTrace()
      }
    }
    context.setAttribute("dis2name", dis2name)
    context.setAttribute("pheno2name", pheno2name)
    context.setAttribute("dismap", dismap)
    context.setAttribute("donames", donames)
    context.setAttribute("name2doid", name2doid)
    context.setAttribute("dermo2mgi", dermo2mgi)
    //    context.setAttribute("mgi2name", mgi2name)

    jetty.start()
  }

  public static void main(args) {
    DoDermoServer s = new DoDermoServer()
    s.run(9998)
  }
}

