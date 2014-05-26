def splits = 5

def s = []
new File("medlinecorpus").eachFile { file ->
  if (file.toString().endsWith("xml")) {
    s << file.toString()
  }
}

def size = s.size()
splits.times { count ->
  print "groovy ParseMedline.groovy $count "
  for (int i = count * size/splits ; i < (count+1)*size/splits ; i++) {
    print s[i]+" "
  }
  println ""
}