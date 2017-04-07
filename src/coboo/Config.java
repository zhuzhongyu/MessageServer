package coboo;

import java.io.File;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

public class Config {
public static final int BUFFER_SIZE = 1024;
public static String message_base_dir="/icare/message";
private static Config instance;
public  int server_port=2001;
private String configfilename="config.xml";
File file ;
private Config(){
	try {
		SAXReader reader = new SAXReader();
		file = new File(configfilename);
		Document doc = reader.read(file);
		server_port = Integer.valueOf(doc.selectSingleNode("//server_port").getText());
	} catch (DocumentException e) {
		e.printStackTrace();
	}
}
public String getConfigFilename(){
	return file.getAbsolutePath();
}
public static Config getInstance(){
	if(instance==null){
		instance=new Config();
	}
	return instance;
}
}
