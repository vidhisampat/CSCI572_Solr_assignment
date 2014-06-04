<?php

$search=$_GET['query'];
$words = split(' AND ', $search);
sort($words);

$str= "*";

for ($x=0; $x<sizeof($words); $x++) {
	$str= $str.$words[$x]."*";  
} 

$url = "http://localhost:8983/solr/collection1/select?q=cat%3A".$str."&rows=888&&wt=xml&indent=true";

//echo $url;
header ("Content-Type:text/xml");
if (($response_xml_data = file_get_contents($url))===false){
    echo "Error fetching XML\n";
} else {
   libxml_use_internal_errors(true);
   echo $response_xml_data;
//	echo $search;

}
?>


