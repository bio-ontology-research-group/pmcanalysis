
def id = ""
def id2name = [:]
new File("ontologies/HumanDO.obo").eachLine { line ->
  if (line.startsWith("id:")) {
    id = line.substring(3).trim()
  }
  if (line.startsWith("name:")) {
    id2name[id] = line.substring(5).trim()
  }
}
new File("ontologies/Dermatology.obo").eachLine { line ->
  if (line.startsWith("id:")) {
    id = line.substring(3).trim()
  }
  if (line.startsWith("name:")) {
    id2name[id] = line.substring(5).trim()
  }
}
id2name.each { k, v ->
  println "$k\t$v"
}