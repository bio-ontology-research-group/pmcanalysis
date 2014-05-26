def id2name = [:]
new File("../drugeffects/label_mapping.tsv").splitEachLine("\t") { line ->
  def name1 = line[0]
  def name2 = line[1]
  def id = line[3]?.replaceAll("-","STITCHTM:")
  if (id) {
    if (name1) {
      if (id2name[id] == null) {
	id2name[id] = new TreeSet()
      }
      id2name[id].add(name1)
    }
    if (name2) {
      if (id2name[id] == null) {
	id2name[id] = new TreeSet()
      }
      id2name[id].add(name2)
    }
  }
}

new File(args[0]).splitEachLine("\t") { line ->
  def id = line[0]
  if (id2name[id]) {
    print "$id\t"
    id2name[id].each { print it+"|" }
    print "\t"
    line[2..-1].each { print it+"\t" }
    println ""
  }
}