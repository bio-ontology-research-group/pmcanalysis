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
4. This is generic and works for any pair of ontologies, or set of ids/labels and ontology. FindDrugEffects.groovy does this for drugs and phenotypes (never published).
