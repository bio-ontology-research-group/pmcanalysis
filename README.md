The steps to run the analysis are as follows:
1. Run IndexMedline or IndexPMC, depending on whether you want to analyze full text or abstracts only

1.1: to run these, you need to download either Medline or PMC and put them in a directory
by default, that's medlinecorpus-2017 for the Medline script, "pmc" for the other; all unzipped; and the IndexMedline script currently indexes both full text and abstracts in a single index
(so use this one)

1.2 Result is a Lucene Index (same as ElasticSearch) with fields pmcid, pmid, title, abstract, text

2. Run FindDiseasePhenotypes2.groovy

2.1 The script takes two arguments: first, the output file, second the name of the field to find co-occurrences in (either "abstract" or "text")

2.2 Change this by looking for "args[0]" and "args[1]" in the script

2.3 add the ontologies to look for co-occurrences between in a subdirectory, look for "parseOntologies(" in the script and adjust

2.4 in line 214 and 216 it specifies which kind of IDs it computes similarities between, currently DOID and HP/MP; change as you see fit

3. The output file has an ontology-based t-score, z-score, lmi, lgl, pmi

3.1 for the implemented measures, the complete list and description is here: http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.471.5863&rep=rep1&type=pdf

4. This is a generic pipleine and works for any pair of ontologies, or set of ids/labels and ontology. FindDrugEffects.groovy does this for drugs and phenotypes (never published).

Text mining results (obtained at the end of the 3rd step above) should be evaluted in order to select a cut-off (e.g. rank based on NPMI score) for the extracts. This can be done by evaluating the text mining dataset againt a selected reference dataset. We applied the following steps for AUC calculation to select the best thereshold for disease-phenotypes:

STEP 0. Run filter_rankList.pl 
this perl script calls  FilterDOPheno.groovy from the pmcanalysis folder for 100 times and generates 100 files with the n (1..100) top phenotypes for the given diseases. 

e.g. "groovy FilterDOPheno doid2hpo.txt 80 will generate a ranked list with the top 80 phenotypes for each disease"
Caution: The input file to be used is hard coded in the perl script.

STEP 1.  generate the complete file from the lucene-scripy and then, run FilterDOPheno.groovy from the pmcanalysis folder (obtainable from https://github.com/bio-ontology-research-group/pmcanalysis):

groovy FilterDOPheno doid2hpo.txt 80 will generate a ranked list with the top 80 phenotypes for each disease
GNU Parallel can be used for this, using parallel -j 64 < parinput.txt
and parinput.txt looks like:
groovy FilterDOPheno doid2hpo.txt 1
groovy FilterDOPheno doid2hpo.txt 2
groovy FilterDOPheno doid2hpo.txt 3
groovy FilterDOPheno doid2hpo.txt 4
groovy FilterDOPheno doid2hpo.txt 5
groovy FilterDOPheno doid2hpo.txt 6
groovy FilterDOPheno doid2hpo.txt 7
groovy FilterDOPheno doid2hpo.txt 8
groovy FilterDOPheno doid2hpo.txt 9
groovy FilterDOPheno doid2hpo.txt 10
groovy FilterDOPheno doid2hpo.txt 11
etc.
this will write new files called "filtered-doid-pheno-[cutoff].txt
for every cutoff so you have to do a seq 100 or something to generate all from 1 to 100


STEP 2. get phenomeblast from github: https://github.com/bio-ontology-research-group/phenomeblast
the folder named smltest contains the scripts

groovy PNRevised.groovy -i InputPath/filtered-doid-pheno-1.txt -o OutputPath/filtered-doid-pheno-1.out.txt
etc.
for all the files you have generated. This creates the ranked list of positives, i.e., tells you at which rank you found the positives

to run PNRevised.groovy on clusters use a shell script like below and submit it to the clusters by using sbatch --array=0-99 scriptname.sh:
/***********************************************************************************/
#!/bin/sh
#SBATCH --mem 100Gb # memory pool for all cores
#SBATCH --time 3-00:00:00 # time, specify max time allocation
#SBATCH -e /scratch/dragon/intel/kafkass/disease_phenotypes/slurm_err/slurm.%N.%j.err # STDERR  
#SBATCH --mail-type=END,FAIL # notifications for job done & fail
#SBATCH --mail-user=senay.kafkas@kaust.edu.sa
#SBATCH --cpus-per-task=3
#SBATCH --job-name=similarity

###     #SBATCH --partition=batch

#SBATCH --array=0-99

echo "Job ID=$SLURM_JOB_ID,  Running task:$SLURM_ARRAY_TASK_ID" 

INPUT_DIR="/scratch/dragon/intel/kafkass/disease_phenotypes"; #path to read
OUTPUT_DIR="/scratch/dragon/intel/kafkass/disease_phenotypes/mgi_doid_sim"; #path to write
values=$(grep "^${SLURM_ARRAY_TASK_ID}:" $INPUT_DIR/filtered-doid-pheno/fileNames.txt) #file containing index:input_file_name

filename=$(echo $values | cut -f 2 -d:)


module load java/1.8.44
module load groovy
groovy $INPUT_DIR/PNRevised.groovy -i $INPUT_DIR/filtered-doid-pheno/$filename -o $OUTPUT_DIR/$filename.out.txt

/***********************************************************************************/


STEP 3. run EvalDOMGI.groovy on the files generated from PNRevised that generated new files

export JAVA_OPTS="-Xms10G -Xmx100G -XX:-UseGCOverheadLimit" --> apply this otherwise you will get heap memory exception

EvalDOMGI.groovy -i /home/kafkass/Projects/pmcanalysis-master/MGI_pheno_sim/filtered-doid-pheno-2.out.txt -p /home/kafkass/Projects/phenomeblast/smltest/MGI-DOID.gold.txt -o test.out 

to run EvalDOMGI.groovy on clusters use a shell script like below and submit it to the clusters by using sbatch --array=0-99 scriptname.sh:
/***********************************************************************************/
#!/bin/sh
#SBATCH --mem 110Gb # memory pool for all cores
#SBATCH --time 3-00:00:00 # time, specify max time allocation
#SBATCH -e /scratch/dragon/intel/kafkass/disease_phenotypes/auc/slurm_err/slurm.%N.%j.err # STDERR  
#SBATCH --mail-type=END,FAIL # notifications for job done & fail
#SBATCH --mail-user=senay.kafkas@kaust.edu.sa
#SBATCH --cpus-per-task=3
#SBATCH --job-name=similarity

###     #SBATCH --partition=batch

#SBATCH --array=0-99


echo "Job ID=$SLURM_JOB_ID,  Running task:$SLURM_ARRAY_TASK_ID" 

INPUT_DIR="/scratch/dragon/intel/kafkass/disease_phenotypes/auc"; #path to read
OUTPUT_DIR="/scratch/dragon/intel/kafkass/disease_phenotypes/auc/rates"; #path to write
values=$(grep "^${SLURM_ARRAY_TASK_ID}:" $INPUT_DIR/fileNames.txt) #file containing index:input_file_name

filename=$(echo $values | cut -f 2 -d:)


module load java/1.8.44
module load groovy
export JAVA_OPTS="-Xms10G -Xmx100G -XX:-UseGCOverheadLimit"
groovy $INPUT_DIR/EvalDOMGI.groovy -i $INPUT_DIR/$filename -p $INPUT_DIR/MGI-DOID.gold.txt -o $OUTPUT_DIR/$filename.rates.txt
/***********************************************************************************/



STEP 4. on the files generated by EvalDOMGI.groovy, run GetAUC.groovy to get the AUC



CAUTION!:
How to generate mousephenotypes.txt which is required as input file for PNRevised.groovy?
The file content looks like:
MGI:1857242     MP:0001716
MGI:97874       MP:0001716
MGI:1857242     MP:0001698
MGI:97874       MP:0001698
MGI:1857242     MP:0001092
MGI:97874       MP:0001092
MGI:1857242     MP:0000961
first column is the GENE or ALLELE identifier and the second column is the MP class

It can be generated by using MakeModelAnnotations.groovy (from phenomeblast/fixphenotypes/). There are several input files required for MakeModelAnnotations.groovy

you get them from MGI and zfin
mouse phenotypes from http://www.informatics.jax.org/informatics.jax.orginformatics.jax.org
[MGI-Mouse Genome Informatics-The international database resource for the laboratory mouse
MGI: the international database resource for the laboratory mouse, providing integrated genetic, genomic, and biological data for researching human health and disease.]

specifically, Download -> All MGI Reports-> Alleles and Phenotypes
there are 2 relevant files:
HMD_HumanPhenotype.rpt
MGI_PhenoGenoMP.rpt

phenotype_annotation.tab can be downloded from http://compbio.charite.de/jenkins/job/hpo.annotations/lastStableBuild/
mim2gene.txt can be downloaded from https://www.omim.org/downloads/  
ortho_XX.txt- > human orthologs can be downloaded from http://zfin.org/downloads --> "Phenotypic Zebrafish genes with Human Orthology"

fishies.txt-> when you generate the phenomenet.owl file, you need to provide fish phenotypes
the reason this needs to be provided when generating the ontology is that fish don't use a single ontology but 2 ontologies, and generating phenomenet.owl will create a new class for each pair of classes used to characterize a fish
the classes start with PHENO_ in the phenomenet.owl ontology
and they are created by using patterns similar to what @salghamdi is using
there is a fishies.txt file generated when you generate the ontology, this rewrites all the annotations of fish from 2 ontologies to the phenomenet.owl ontology
How to generate phenoment.owl and fishes.txt? (inferrred version of phenomenet which is required as the input file for PNRevised.groovy?)
Please see the documentation on how to generate phenoment.owl at https://github.com/bio-ontology-research-group/phenomenet/wiki

run MakeModelAnnotations.groovy >outfile.txt
run perl format_mousephenotypes.pl > mousephenotypes.txt (the perl script is in phenomeblast/fixphenotypes/) to have the reformatted version of the file which can be used with the PNRevised.groovy



NOTE!:
STEPS 2 and 3 can be used to calculate the similarities for the entities in the phenoment. The semantic simialrity scores can be used to ran the top 100 relations and represent them through the phenomet web interface
