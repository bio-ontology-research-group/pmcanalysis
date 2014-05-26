def NUMSENTENCES = 60478190
def SIZE = 1000 // size of eval corpus

Integer hit = NUMSENTENCES/SIZE
def count = 0
new File("results/medline-sentences").eachLine { line ->
  if (count % hit == 0) {
    def oline = line
    line = line.split("\t")
    if (line[1].indexOf(line[3])==-1 && line[3].indexOf(line[1])==-1 && line[3]!="chronic" && line[3]!="death" && line[1].indexOf("disease")==-1 && line[3].indexOf("disease")==-1) {
      println oline
    }
  }
  count += 1
}
