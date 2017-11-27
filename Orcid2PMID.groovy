
// file to annotate with ORCID
def infile = new File(args[0])


XmlSlurper slurper = new XmlSlurper(false, false)
slurper.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
slurper.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)

def pmid2orcid = [:].withDefault { new LinkedHashSet() }
new File("xml").eachFile { of ->
  def orcid = slurper.parse(of)
  def oid = orcid."orcid-profile"."orcid-identifier".path.text()
  def pmids = orcid."orcid-profile"."orcid-activities"."orcid-works"."orcid-work".each { work ->
    def pmid = work.@"put-code"
    pmid2orcid[pmid].add(oid)
  }
}

def counter = 0
infile.splitEachLine("\t") { line ->
  def initials = line[0]
  def lastname = line[1]
  def author = null
  line[2..-1].each { pmid ->
    if (pmid2orcid[pmid] != null) {
      author = "<http://orcid.org/"+pmid2orcid[pmid]+">"
    }
  }
  if (author == null) {
    author = "<http://aber-owl.net/authors#$counter>"
    counter += 1
  }

  println("$author <http://www.w3.org/2000/01/rdf-schema#label> \"$initials $lastname\" .")
  line[2..-1].each { pmid ->
    println("$author <http://aber-owl.net/authors#has-publication> <http://www.ncbi.nlm.nih.gov/pubmed/$pmid> .")
  }
}
