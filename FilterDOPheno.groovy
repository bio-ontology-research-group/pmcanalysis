// this computes the geometric mean of the ranks based on individual scores and filters by cutoff


def cutoff = new Double(args[1])
def mincooc = 3

def fout = new PrintWriter(new BufferedWriter(new FileWriter("filtered-doid-pheno-" + args[1] + ".txt")))

def l = []
def doid = ""
def name1 = ""
def name2 = ""
new File(args[0]).splitEachLine("\t") { line ->
  def id = line[0]
  def mp = line[1]
  def oldname1 = name1
  def oldname2 = name2
  name1 = line[9]
  name2 = line[10]
  if (id != doid) {
    def lts = l.sort { it.tscore }.reverse() //.indexOf(exp) / lsize
    def lpmi = l.sort { it.pmi }.reverse() //.indexOf(exp) / lsize
    def lzs = l.sort { it.zscore }.reverse() //.indexOf(exp) / lsize
    def llmi = l.sort { it.lmi }.reverse() //.indexOf(exp) / lsize
    def llgl = l.sort { it.lgl } //.indexOf(exp) / lsize
    def lsize = l.size()
    
    for (int i = 0 ; i < lsize ; i++) {
      lts[i].score1 = i/lsize
      lpmi[i].score2 = i/lsize
      lzs[i].score3 = i/lsize
      llmi[i].score4 = i/lsize
      llgl[i].score5 = i/lsize
    }
    for (int i = 0 ; i < lsize ; i++) {
      def mean = (l[i].score1+l[i].score2+l[i].score3+l[i].score4+l[i].score5)/5
      if (mean < cutoff && l[i].cooc>mincooc) {
	fout.println("$doid\t${l[i].mp}\t${l[i].tscore}\t${l[i].zscore}\t${l[i].lmi}\t${l[i].pmi}\t${l[i].lgl}\t$mean\t$oldname1\t${l[i].name}")
      }
    }
    doid = id
    l = []
  }
  try {
    Expando exp = new Expando()
    exp.mp = mp
    exp.name = line[10]
    exp.tscore = new Double(line[2])
    exp.zscore = new Double(line[3])
    exp.lmi = new Double(line[4])
    exp.pmi = new Double(line[5])
    exp.lgl = new Double(line[6])
    exp.cooc = new Integer(line[7])
    l << exp
  } catch (Exception E) {}
}
fout.flush()
fout.close()
