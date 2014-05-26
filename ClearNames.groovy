def id = ""
def id2name = [:]
new File("ontologies").eachFile { file ->
  file.eachLine { line ->
    if (line.startsWith("id:")) {
      id = line.substring(3).trim()
    }
    if (line.startsWith("name:")) {
      id2name[id] = line.substring(5).trim()
    }
  }
}

new File(args[0]).splitEachLine("\t") { line ->
  println id2name[line[0]]+"\t"+id2name[line[1]]+"\t"+line[2]+"\t"+line[3]
}