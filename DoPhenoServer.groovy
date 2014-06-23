@Grapes([
	  @Grab('org.eclipse.jetty:jetty-server:9.0.0.M5'),
	  @Grab('org.eclipse.jetty:jetty-servlet:9.0.0.M5'),
	  @Grab('javax.servlet:javax.servlet-api:3.0.1'),
	  @GrabExclude('org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016'),
	  @GrabConfig(systemClassLoader=true)
	])

import groovy.json.*
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import groovy.servlet.*
import javax.servlet.http.*
import javax.servlet.ServletConfig

class DoPhenoServer extends HttpServlet {
  public final static String disfilename = "doid2hpo.txt"
  def requestHandler
  def context
  void init(ServletConfig config) {
    super.init(config)
    context = config.servletContext


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
    context.addServlet(GroovyServlet, '/DoPhenoInterface.groovy')
    context.setAttribute('version', '1.0')  
    SuggestTree donames = new SuggestTree(500, new HashMap<String, Integer>());

    Map dismap = [:].withDefault { new LinkedHashSet() }
    Map dis2name = [:].withDefault { new TreeSet() }
    Map name2doid = [:].withDefault { new TreeSet() }
    Map pheno2name = [:].withDefault { new TreeSet() }
    println "Reading file..."
    new File(disfilename).splitEachLine("\t") { line ->
      def doid = line[0]
      Expando exp = new Expando()
      exp.mp = line[1]
      exp.tscore = new Double(line[2])
      exp.zscore = new Double(line[3])
      exp.lmi = new Double(line[4])
      exp.pmi = new Double(line[5])
      exp.cooc = new Integer(line[6])
      exp.mpnames = []
      dismap[doid].add(exp)
      def dname = line[9].trim()
      dname = dname.substring(1, dname.length()-1)
      def pname = line[10]
      pname = pname.substring(1, pname.length()-1)
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
      pname.split(",").each { 
	pheno2name[exp.mp].add(it.trim()) 
	exp.mpnames << it.trim()
      }
    }
    context.setAttribute("dis2name", dis2name)
    context.setAttribute("pheno2name", pheno2name)
    context.setAttribute("dismap", dismap)
    context.setAttribute("donames", donames)
    context.setAttribute("name2doid", name2doid)

    jetty.start()
  }

  public static void main(args) {
    DoPhenoServer s = new DoPhenoServer()
    s.run(9999)
  }
}
