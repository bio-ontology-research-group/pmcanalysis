println "!DB, Object ID, Object Symbol, Qualifier (score), Phenotype ID, Reference, Evidence Code, <empty>, Aspect, DB Object Name, DB Object Synonym, DB Object Type, Taxon, Date, Assigned By, Annotation Extension, Gene Product Form ID"

new File(args[0]).splitEachLine("\t") { line ->
  def doid = line[0]
  def pheno = line[1]
  def evidence = line[5]
  def disname = line[8].replaceAll("\\[","").replaceAll("\\]","").split(",")
  def phenoname = line[0].replaceAll("\\[","").replaceAll("\\]","").split(",")
  println "Aber-OWL\t$doid\t"+disname[0]+"\t$evidence\t$pheno\thttp://arxiv.org/abs/1411.0450\tITM\t\t\t"+disname[0]+"\t"+disname+"\tdisease\ttaxon:9606\t6/1/2015\tRobert Hoehndorf\t\t"
}