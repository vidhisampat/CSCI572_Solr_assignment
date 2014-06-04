import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer; //Changed in solr 4
import org.apache.solr.client.solrj.request.AbstractUpdateRequest;
import org.apache.solr.client.solrj.request.ContentStreamUpdateRequest;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.geonames.Toponym;
import org.geonames.ToponymSearchCriteria;
import org.geonames.ToponymSearchResult;
import org.geonames.WebService;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class TikaGeoTagger {
	List<String> keywords;
	PrintWriter logfile;
	int num_keywords, num_files, num_fileswithkeywords;
	Map<String, Integer> keyword_counts;
	Date timestamp;
	static int count = 0;
	// String docNames[] = new String[15];
	int numDocs[] = new int[18];

	private static final String APPLICATION_PDF = "application/pdf";
	private static final int MIN_WORD_LEN = 3;
	Map<String, Integer> uniqueWordFreqMap;
	TreeMapValueComparator comparator;
	TreeMap<String, Integer> sortedMap;
	Map<String, Set<String>> fileNameFileKeywordsMap;
	Tika tika = new Tika();
	final List<String> stopWords = Arrays.asList("a", "an", "and", "are", "as",
			"at", "be", "but", "by", "for", "if", "in", "into", "is", "it",
			"no", "not", "of", "on", "or", "such", "that", "the", "their",
			"then", "there", "these", "they", "this", "to", "was", "will",
			"with");
	String UNICODE_CHAR_PATTERN = "^[\\p{L}0-9]*$";

	public TikaGeoTagger() {
		keywords = new ArrayList<String>();
		fileNameFileKeywordsMap = new HashMap<>();
		num_keywords = 0;
		num_files = 0;
		count = 0;
		num_fileswithkeywords = 0;
		keyword_counts = new HashMap<String, Integer>();
		timestamp = new Date();
		try {
			logfile = new PrintWriter("log.txt");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
		TikaGeoTagger instance = new TikaGeoTagger();
		instance.run();
	}

	@SuppressWarnings("unchecked")
	private void run() throws IOException {
		// Open all pdf files, process each one
		try {
			BufferedReader keyword_reader = new BufferedReader(new FileReader(
					"keywords.txt"));
			String str;
			while ((str = keyword_reader.readLine()) != null) {
				keywords.add(str);
				num_keywords++;
				keyword_counts.put(str, 0);
			}
			keyword_reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		FileInputStream fis = new FileInputStream("fNameFKey.ser");
		ObjectInputStream ois = new ObjectInputStream(fis);
		try {
			fileNameFileKeywordsMap = (HashMap<String, Set<String>>) ois
					.readObject();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		ois.close();

		// Open all pdf files, process each one
		File pdfdir = new File("./vault");
		File[] pdfs = pdfdir.listFiles(new PDFFilenameFilter());
		for (File pdf : pdfs) {
			processFile(pdf);
			
			//code to run Tika on the vault to fetch relevant files
			/*
			int check = getKeywords(pdf);
			if (check != -1) {
				String source = "./vault/" + pdf.getName();
				File sourceFile = new File(source);
				String name = sourceFile.getName();
				switch (check) {
				case 0:
				case 1:
				case 2:
				case 3:
				case 4:
					File targetFile = new File(target + name);
					FileUtils.copyFile(sourceFile, targetFile);
					break;
				case 5:
				case 6:
				case 7:
				case 8:
				case 9:
				case 10:
				case 11:
					File targetFile1 = new File(target1 + name);
					FileUtils.copyFile(sourceFile, targetFile1);
					break;
				case 12:
				case 13:
				case 14:
				case 15:
				case 16:
				case 17:
					File targetFile2 = new File(target2 + name);
					FileUtils.copyFile(sourceFile, targetFile2);
					break;
				}
			}*/

		}

		//code for serialization
		 /*FileOutputStream fos = new FileOutputStream("fNameFKey.ser");
		 ObjectOutputStream oos = new ObjectOutputStream(fos);
		 oos.writeObject(fileNameFileKeywordsMap);
		 oos.close();*/
	}

	private boolean getGeoTag(String fileName, String word,
			String parentFolderName, String keywordList) {
		WebService.setUserName("anoopprk");

		ToponymSearchCriteria searchCriteria = new ToponymSearchCriteria();
		searchCriteria.setQ(word);
		ToponymSearchResult searchResult = null;
		try {
			searchResult = WebService.search(searchCriteria);
		} catch (Exception e) {
			e.printStackTrace();
		}
		for (Toponym toponym : searchResult.getToponyms()) {
			double lat = toponym.getLatitude();
			double lon = toponym.getLongitude();
			System.out.println(toponym.getName() + " "
					+ toponym.getCountryName() + " " + lat + " " + lon);

			File indexedFile = new File("vault/" + fileName);
			indexFileInSolr(indexedFile.getAbsolutePath(), fileName, lat, lon,
					parentFolderName, keywordList);
			return true;
		}

		return false;
	}

	private void indexFileInSolr(String fileName, String solrId, double lat,
			double lon, String parentFolderName, String keywordList) {

		String urlString = "http://localhost:8983/solr"; // Change this
		SolrServer solr = new HttpSolrServer(urlString);

		ContentStreamUpdateRequest up = new ContentStreamUpdateRequest(
				"/update/extract");

		try {
			File f = new File(fileName);
			up.addFile(f, APPLICATION_PDF);
			up.setParam("literal.cat", keywordList);
		} catch (IOException e) {
			e.printStackTrace();
		}

		up.setParam("literal.id", solrId);
		up.setParam("uprefix", "attr_");
		up.setParam("fmap.content", "attr_content");

		up.setParam("literal.weight", new Double(lat).toString());
		up.setParam("literal.price", new Double(lon).toString());

		up.setAction(AbstractUpdateRequest.ACTION.COMMIT, true, true);

		try {
			solr.request(up);
		} catch (SolrServerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int getKeywords(File f) {
		PDFParser pdf = new PDFParser();
		InputStream input;
		int index = -1;
		boolean isKeywordPresent = false;
		Set<String> keywordSet = new HashSet<>();
		try {
			// parse the PDF file passed to this method
			input = new FileInputStream(f);
			ContentHandler ch = new BodyContentHandler(2147483647);
			Metadata meta = new Metadata();
			ParseContext pc = new ParseContext();
			pdf.parse(input, ch, meta, pc);
			// convert the entire PDF to a single string
			String fileText = ch.toString();
			// split the string on all characters except alphabet
			fileText = fileText.toLowerCase();
			int numKeywords[] = new int[keywords.size()];
			for (int i = 0; i < keywords.size(); i++) {
				// docNames[i] = "";
				while (!fileText.equals("")) {
					int x = fileText.indexOf(keywords.get(i));
					if (x != -1) {
						keyword_counts.put(keywords.get(i),
								keyword_counts.get(keywords.get(i)) + 1);
						fileText = fileText.substring(x
								+ keywords.get(i).length(), fileText.length());
						updatelog(keywords.get(i), f.getName());
						isKeywordPresent = true;
						numKeywords[i]++;

						keywordSet.add(keywords.get(i));
					} else
						break;
				}
			}
			int maxindex = -1;
			int max = -1;
			for (int i = 0; i < numKeywords.length - 1; i++)
				if (numKeywords[i] > max) {
					maxindex = i;
					max = numKeywords[i];
				}
			index = maxindex;
			// increase the count for the files containing keywords
			if (isKeywordPresent)
				num_fileswithkeywords++;
			input.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (TikaException e) {
			e.printStackTrace();
		}

		fileNameFileKeywordsMap.put(f.getName(), keywordSet);
		return index;
	}

	private void processFile(File f) {
		InputStream is = null;
		uniqueWordFreqMap = new HashMap<String, Integer>();
		comparator = new TreeMapValueComparator(uniqueWordFreqMap);
		sortedMap = new TreeMap<String, Integer>(comparator);

		try {
			is = new BufferedInputStream(new FileInputStream(f));

			Parser parser = new PDFParser();
			// ContentHandler handler = new BodyContentHandler(System.out);
			ContentHandler handler = new BodyContentHandler(10 * 1024 * 1024);

			Metadata metadata = new Metadata();

			parser.parse(is, handler, metadata, new ParseContext());
			String content = handler.toString();

			if (!content.equals("")) {
				// System.out.println(content);
				Pattern p = Pattern.compile(UNICODE_CHAR_PATTERN);
				String[] words = content.toLowerCase().split("\\s+");
				for (int i = 0; i < words.length; i++) {
					if (!stopWords.contains(words[i])
							&& (words[i].length() > MIN_WORD_LEN)) {
						Matcher m = p.matcher(words[i]);
						if (m.find()) {
							int count = uniqueWordFreqMap.containsKey(words[i]) ? uniqueWordFreqMap
									.get(words[i]) : 0;
							uniqueWordFreqMap.put(words[i], count + 1);
						}
					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (TikaException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		StringBuffer sbr = new StringBuffer();
		Set<String> keywordSet = fileNameFileKeywordsMap.get(f.getName());
		for (String s : keywordSet) {
			sbr.append(s + ",");
		}

		sortedMap.putAll(uniqueWordFreqMap);
		for (Map.Entry<String, Integer> entry : sortedMap.entrySet()) {
			String key = entry.getKey();
			// Integer value = entry.getValue();
			if (getGeoTag(f.getName(), key, f.getParentFile().getName(),
					sbr.toString())) {
				break;
			}
		}
	}

	static class PDFFilenameFilter implements FilenameFilter {
		private Pattern p = Pattern.compile(".*\\.pdf",
				Pattern.CASE_INSENSITIVE);

		public boolean accept(File dir, String name) {
			Matcher m = p.matcher(name);
			return m.matches();
		}
	}

	private void updatelog(String keyword, String filename) {
		timestamp.setTime(System.currentTimeMillis());
		logfile.println(timestamp + " -- \"" + keyword + "\" found in file \""
				+ filename + "\"");
		logfile.flush();
	}

	class TreeMapValueComparator implements Comparator<String> {

		Map<String, Integer> base;

		public TreeMapValueComparator(Map<String, Integer> base) {
			this.base = base;
		}

		public int compare(String a, String b) {
			if (base.get(a) >= base.get(b)) {
				return -1;
			} else {
				return 1;
			} // returning 0 would merge keys
		}
	}
}
