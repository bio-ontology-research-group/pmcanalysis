use strict;

my $i; my $cmd;

for ($i=3; $<=100; $i++)
{print "count=".$i."\n";
$cmd="groovy FilterDOPheno.groovy disease_phenotypes.6May2018_new.txt $i";
system ($cmd);

}
