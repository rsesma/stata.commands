package commands;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.apache.commons.io.FileUtils;


public class Commands {

	public static final String D_DISTRIBUTION_DATE = "d Distribution-Date: ";
	public static final String D_VERSION = "d Version ";
	public static final String VERSION = "*! VERSION";
	public static final String ALL = "_all";
	public static final String EXTRACT = "_extract";
	
	public static File dir;
	public static File dest = null;
	
	public static void main(String[] args) {
		File xtract = null;
		
		if (args.length == 0) {
			System.err.println("Error; missing parameters");
    	} else {
    		// third param is optional: working directory
    		if (args.length == 3) {
    			dir = new File(args[2]);    			
    		} else {
    			// by default: user folder / git / stata
    			dir = new File(System.getProperty("user.home"));
    			dir = new File(dir, "git");
    			dir = new File(dir, "stata");
    		}
    		
    		// if second param = EXTRACT, fourth param is extract folder
    		if (args[1].equalsIgnoreCase(EXTRACT)) {
	    		if (args.length == 4) {
	    			xtract = new File(args[3]);    			
	    		} else {
	    			// by default: user folder / Desktop
	    			xtract = new File(System.getProperty("user.home"));
	    			xtract = new File(xtract, "Desktop");
            		dest = new File(xtract,args[0]);
            		try {
            			FileUtils.forceMkdir(dest);
                    } catch (Exception e) {
                    	e.printStackTrace();
                    }	
	    		}
    		}
    		    		
            try {
            	// connect to database
                String url = "jdbc:mariadb://localhost:3306/commands";
                Properties info = new Properties();
                info.setProperty("user", "rsesma");
                info.setProperty("password", "Amsesr.1977");
                Connection conn = DriverManager.getConnection(url, info);
                // get command data
                PreparedStatement q;
                if (!args[0].equalsIgnoreCase(ALL)) {
                    q = conn.prepareStatement("SELECT * FROM list WHERE NAME = ?");
                    q.setString(1, args[0]);
                } else {
                    q = conn.prepareStatement("SELECT * FROM list");
                }                
                ResultSet rs = q.executeQuery();
                // loop through commands and update
                while(rs.next()){
                	String command = rs.getString("NAME");
                	if (args[1].equalsIgnoreCase(EXTRACT)) {
                		copyFiles(command);
                	} else {
	            		String comment = args[1];
	                	String old_v = rs.getString("VERSION");
	                	String old_d = rs.getString("date");
	            		// new version
	                	String v = getNextVersion(old_v);
	                	// new version date: today
	            		String d = new SimpleDateFormat("ddMMMyyyy", Locale.ENGLISH).format(new Date()).toLowerCase();
	                	// update command files
	            		updateCommand(command, v, d, old_v, old_d);
	            		// update database: versions table
	    	            q = conn.prepareStatement("INSERT INTO versions " +
	  						  "(name,version,date,comment) VALUES(?,?,?,?)");
	    	            q.setString(1,command);
	    	            q.setString(2,v);
	    	            q.setString(3,d);
	    	            q.setString(4,comment);
	    	            q.executeUpdate();
                	}
                }
                // close database
                rs.close();
                rs = null;
                conn.close();
                conn = null;
        		
        		System.out.println("Proceso finalizado");
            } catch (Exception e) {
            	e.printStackTrace();
            }
    	}
	}

	public static void copyFiles(String command) {
    	File folder = new File(dir, command);
        FilenameFilter filter = new FilenameFilter(){
    		public boolean accept(File dir, String name) {
    			return name.toLowerCase().endsWith(".ado") ||  name.toLowerCase().endsWith(".dlg") || 
    					name.toLowerCase().endsWith(".sthlp") || name.toLowerCase().endsWith(".pkg");
    		}
        };
		File files[] = folder.listFiles(filter);
		for (File f : files) {
			try {
				FileUtils.copyFile(f, new File(dest,f.getName()));
				System.out.println("Copiado archivo " + f.getAbsolutePath());
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
		}        
	}

	
	public static void updateCommand(String command, String v, String d, String old_v, String old_d) {
		updatePKG(command, v, d);
		updateADO_DLG(command, v, d, old_v, old_d);
		updateSTHLP(command, v, d, old_v, old_d);
		
		System.out.println("Comando " + command + " actualizado");
	}
		
	
	public static void updateSTHLP(String command, String v, String d, String old_v, String old_d) {
    	File folder = new File(dir, command);
        FilenameFilter filter = new FilenameFilter(){
    		public boolean accept(File dir, String name) {
    			return name.toLowerCase().endsWith(".sthlp");
    		}
        };
        
        // DateFormat short/long to update long version date 
        DateFormat df = new SimpleDateFormat("ddMMMyyyy", Locale.ENGLISH);
        DateFormat dfLong = new SimpleDateFormat("dd MMMMMM yyyy", Locale.ENGLISH);
		
	    //List of all sthlp files
		File files[] = folder.listFiles(filter);
		for (File f : files) {
			try {
	            String c = FileUtils.readFileToString(f,"UTF-8");		// read text file
	            // update version and short date
	            c = c.replace(old_v,v).replace(old_d,d);
	            // update long date
	            c = c.replace(dfLong.format(df.parse(old_d)),dfLong.format(df.parse(d)));
	            // update year of Vancouver Reference
	            String y_old = "de Barcelona; " + old_d.substring(old_d.length()-4) + ".{break}";
	            String y_new = "de Barcelona; " + d.substring(d.length()-4) + ".{break}";
	            c = c.replace(y_old,y_new);

	            FileUtils.writeStringToFile(new File(f.getAbsolutePath()), c, Charset.forName("UTF-8"));
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
		}        
	}
	
    public static void updateADO_DLG(String command, String v, String d, String old_v, String old_d) {
    	
    	File folder = new File(dir, command);
        FilenameFilter filter = new FilenameFilter(){
    		public boolean accept(File dir, String name) {
    			return name.toLowerCase().endsWith(".ado") || name.toLowerCase().endsWith(".dlg");
    		}
        };
        
	    //List of all ado/dlg files
		File files[] = folder.listFiles(filter);
		for (File f : files) {
			try {
	            List<String> lines = FileUtils.readLines(f,"UTF-8");
	            List<String> result = new ArrayList<>();            	
	            for (String line : lines) {
	            	if (line.toUpperCase().indexOf(VERSION)!=-1) {
	            		result.add(line.replace(old_v,v).replace(old_d,d));
	            	}
	            	else result.add(line);
	            }
	            
	            Files.write(Paths.get(f.getAbsolutePath()), result, Charset.forName("UTF-8"));
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
		}
    }

    public static void updatePKG(String command, String v, String d) {
    	
    	File pkg = new File(dir, command);
    	pkg = new File(pkg, command + ".pkg");
		try {
            List<String> lines = FileUtils.readLines(pkg, "UTF-8");
            List<String> result = new ArrayList<>();
            for (String line : lines) {
            	if (line.indexOf(D_DISTRIBUTION_DATE )!=-1) result.add(D_DISTRIBUTION_DATE + d);
            	else if (line.indexOf(D_VERSION)!=-1) result.add(D_VERSION + v + " (" + d + ")");            		
            	else result.add(line);
            }
            
            Files.write(Paths.get(pkg.getAbsolutePath()), result, Charset.forName("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
	
    public static String getNextVersion(String v) {
    	
    	int i1 = 0;
    	int i2 = 0;
    	int i3 = 0;
    	
    	// read string as integer
    	int j = 1;
    	for (String s : v.replace(".",",").split(",")) {
    		if (j==1) i1 = Integer.parseInt(s);
    		if (j==2) i2 = Integer.parseInt(s);
    		if (j==3) i3 = Integer.parseInt(s);
    		j++;
    	}
    	
    	// increase version number
    	if (i3<9) {
    		i3++;
    	} else {
    		i3 = 0;
    		if (i2<9) {
    			i2++;
    		} else {
    			i2 = 0;
    			i1++;
    		}
    	}
    	
    	return Integer.toString(i1) + "." + Integer.toString(i2) + "." + Integer.toString(i3); 
    }


    /*		
	
    FileFilter folderFilter = new FileFilter() {
        public boolean accept(File file) {
            return file.isDirectory();
        }
    };
    
    FilenameFilter pkgFilter = new FilenameFilter(){
		public boolean accept(File dir, String name) {
			return name.toLowerCase().endsWith(".pkg");
		}
    };
		
    File[] folders = dir.listFiles(folderFilter);
    // List<String> foldersInDirectory = new ArrayList<String>(directoryListAsFile.length);
    for (File folder : folders) {
	    //List of all the text files
		File pkgs[] = folder.listFiles(pkgFilter);
		if (pkgs.length > 0) {
			File f = pkgs[0];
			try {
	            List<String> lines = FileUtils.readLines(f, "UTF-8");
	            String c = lines.get(1);
	            String descrip = c.substring(c.indexOf(".")+1).trim().replace(".", "");					
	            String version = "";
	            String date = "";
	            for (String line : lines) {
	            	if (line.indexOf("Distribution-Date:")>0) date = line.substring(line.indexOf(":")+1).trim();
	            	if (line.indexOf("Version")>0) {
	            		version = line.split("Version")[1]; 
	            		version = version.substring(0,version.indexOf("(")).trim();
	            	}
	            }
	            String name = f.getName().replace(".pkg", "");
	            if (!name.equals("ci2p") && !name.equals("getaccess")) {
	            	System.out.println("('" + name + "', '" + version + "', '" + date + "'),");
	            }
	        } catch (IOException e) {
	            e.printStackTrace();
	        }
		}
    }*/
}
