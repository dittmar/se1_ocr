package ocr_pdf;

import java.io.FileInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import static java.lang.Runtime.getRuntime;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;
/**
 *
 * @author Kevin Dittmar
 */
public class OCR_PDF
{
    private ArrayList<String> elevs;
    private ArrayList<Double> angles;
    private ArrayList<String> runways;
    private ArrayList<Double> valid_angles;
    private String variation;
    private String airport;
    private String city_state;
    
    public OCR_PDF()
    {
        elevs = new ArrayList<String>();
        angles = new ArrayList<Double>();
        runways = new ArrayList<String>();
        valid_angles = new ArrayList<Double>();
        variation = "";
        airport = "";
        city_state = "";
    }
    /**
     * Get PDFBox output of airport diagram.
     * @param fileName name of airport diagram PDF file.
     * @return text representation of airport diagram.
     */
    static String getTextPDFBox(String fileName)
    {
        PDFParser parser;
        String parsedText = null;
        PDFTextStripper pdfStripper = null;
        PDDocument pdDoc = null;
        COSDocument cosDoc = null;
        File file = new File(fileName);
        if (!file.isFile()) 
        {
            System.err.println("File " + fileName + " does not exist.");
            return null;
        }
        try 
        {
            parser = new PDFParser(new FileInputStream(file));
        } 
        catch (IOException e) {
            System.err.println("Unable to open PDF Parser. " + e.getMessage());
            return null;
        }
        try
        {
            parser.parse();
            cosDoc = parser.getDocument();
            pdfStripper = new PDFTextStripper();
            //true doesn't work so well.
            pdfStripper.setSortByPosition(false);
            pdDoc = new PDDocument(cosDoc);
            parsedText = pdfStripper.getText(pdDoc);
        } 
        catch (Exception e) 
        {
            System.err.println("An exception occured in parsing the PDF Document."
                                + e.getMessage());
        }
        finally 
        {
            try 
            {
                if (cosDoc != null)
                    cosDoc.close();
                if (pdDoc != null)
                    pdDoc.close();
            } 
            catch (Exception e) 
            {
                e.printStackTrace();
            }
        }
        return parsedText;
    }
    
    /**Uses PDFBox to get a list of valid angles, since pdftotext doesn't
     * currently transpose degree signs (might be an option for this).
     * @param filename the name of the PDF Airport Diagram to parse for angles.
     */
    public void parseAngleList(String filename)
    {
        String pdf_text = getTextPDFBox(filename);
        Scanner scanner = new Scanner(pdf_text);
        Pattern angle_pattern = Pattern.compile("(\\d\\d\\d\\.\\d?)°");
        String next_line = "";
        while (scanner.hasNextLine())
        {
            //"\d\d\d\.\d°" is the regex to get all of the angles
            next_line = scanner.nextLine();
            Matcher matcher = angle_pattern.matcher(next_line);
            if (matcher.find())
            {
                double angle = Double.parseDouble(matcher.group(1));
                if (!valid_angles.contains(angle))
                {
                    valid_angles.add(angle);
                }
            }
        }
    }

    /**This method implements the Xpdf pdftotext.exe command.
     * pdftotext is part of the GPL licensed Xpdf package, which was developed
     * by FooLabs according to the site documentation.
     * TODO:  Find the appropriate way to document the usage of this free
     * software.
     * @param argument the file argument passed to pdftotext
     */
    public static void pdfToText(String argument)
    {
        try
        {
            getRuntime().exec("pdftotext " + argument);
        }
        catch (IOException ex)
        {
            Logger.getLogger(OCR_PDF.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Get the runway names from the pdftotext output.
     * @param filename the name of the file where pdftotext output is stored.
     */
    public void getRunways(String filename)
    {
        File file = new File(filename);
        try
        {
            Scanner scanner = new Scanner(file);
            String next_line = "";
            Pattern rwy_pattern = Pattern.compile(
                    " RWY ([0-9]+[RL]*-[0-9]+[RL]?)"
            );
            while (scanner.hasNextLine())
            {
                next_line = scanner.nextLine();
                next_line = " " + next_line;
                Matcher matcher = rwy_pattern.matcher(next_line);
                if (matcher.find())
                {
                    //This will always be a set of two.
                    String rwy_set[] = matcher.group(1).split("-");
                    runways.add(rwy_set[0]);
                    runways.add(rwy_set[1]);
                }
            }
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(OCR_PDF.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**Get the angles and elevations for runways.
     * 
     * @param filename the filename where the pdftotext parse is stored.
     */
    public void getAnglesAndElevs(String filename)
    {
        File file = new File(filename);
        int elev_counter = 0;
        int elev_index = 0;
        String field_elev = "";
        try
        {
            Scanner scanner = new Scanner(file);
            String next_line = "";
            Pattern elev_pattern = Pattern.compile(".*\\b[A-Za-z]*(\\d{3,4}?)\\b.*");
            Pattern angle_pattern = Pattern.compile("(\\d\\d\\d\\.\\d?)");
            while (scanner.hasNextLine())
            {
                next_line = scanner.nextLine();
                //Make copies of the next_line for each scraper
                String elev_line = next_line;
                String angle_line = next_line;
                //START OF ELEVS
                elev_line = elev_line.replaceAll("\\d+\\.\\d+", "");
                elev_line = elev_line.replaceAll(".*X[ ]\\d+", "");
                Matcher elev_matcher = elev_pattern.matcher(elev_line);
                if (elev_line.contains("ELEV"))
                {
                    if (elev_line.contains("FIELD ELEV"))
                    {
                        elev_index = elevs.size();
                        if (elev_matcher.find())
                        {
                            field_elev = elev_matcher.group(1);
                        }
                    }
                    else
                    {
                        elev_counter++;
                    }
                }
                if (elev_matcher.find() && elev_counter > 0)
                {
                    elevs.add(elev_matcher.group(1));
                    elev_index++;
                    elev_counter--;
                }
                //END OF ELEVS
                //START OF ANGLES
                Matcher angle_matcher = angle_pattern.matcher(angle_line);
                if (angle_matcher.find())
                {
                    double angle = Double.parseDouble(angle_matcher.group(1));
                    if (valid_angles.contains(angle))
                    {
                        angles.add(angle);
                    }
                }
            }
            //We're missing an elevation.
            if (elevs.size() < angles.size())
            {
                //The field elevation probably counted for something.
                elevs.add(elev_index, field_elev);
            }
            //If some angles were read out of order, fix them.
            fixAngleList();
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(OCR_PDF.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**If the angles don't alternate properly, they get switched
     * 
     */
    public void fixAngleList()
    {
        int low_index = -1;
        int high_index = -1;
        double low_angle_pair[] = {-1.0, -1.0};
        double high_angle_pair[] = {-1.0, -1.0};
        for (int i = 0; i < angles.size() - 1; i++)
        {
            if (angles.get(i) < 180 && angles.get(i + 1) < 180)
            {
                low_index = i;
                low_angle_pair[0] = angles.get(i);
                low_angle_pair[1] = angles.get(i + 1);
            }
            if (angles.get(i) > 180 && angles.get(i + 1) > 180)
            {
                high_index = i;
                high_angle_pair[0] = angles.get(i);
                high_angle_pair[1] = angles.get(i + 1);
            }
        }
        
        if (Math.abs(high_index - low_index) == 2)
        {
            angles.set(low_index + 1, high_angle_pair[0]);
            angles.set(high_index, low_angle_pair[1]);
        }
    }
    
    /**
     * Get miscellaneous data, like the airport name, the city and state, and
     * the variation from true north.
     * @param filename the name of the file with pdftotext output.
     */
    public void getMiscData(String filename)
    {
        File file = new File(filename);
        try
        {
            Scanner scanner = new Scanner(file);
            Pattern variation_pattern = Pattern.compile("VAR.*(\\d\\.\\d[NSWE ]+).*");
            Pattern airport_pattern = Pattern.compile("(.*INTL\\(...\\)?)");
            Pattern city_state_pattern = Pattern.compile("([A-Z ]+, [A-Z ]+)");
            String next_line = "";
            while (scanner.hasNextLine())
            {
                //We got them all.
                if (!variation.equals("") && 
                    !airport.equals("") &&
                    !city_state.equals(""))
                {
                    break;
                }
                next_line = scanner.nextLine();
                Matcher variation_matcher = variation_pattern.matcher(next_line);
                Matcher airport_matcher = airport_pattern.matcher(next_line);
                Matcher city_state_matcher = city_state_pattern.matcher(next_line);
                if (variation.equals("") && variation_matcher.find())
                {
                    variation = variation_matcher.group(1);
                }
                if (airport.equals("") && airport_matcher.find())
                {
                    airport = airport_matcher.group(1);
                }
                if (city_state.equals("") && city_state_matcher.find())
                {
                    city_state = city_state_matcher.group(1);
                }
            }
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(OCR_PDF.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    public static void main(String args[])
    {
        OCR_PDF instance = new OCR_PDF();
        //commented instance.methods work
        instance.getRunways("resources/ATL/00026AD_reg.txt");
        instance.parseAngleList("resources/ATL/00026AD.pdf");
        instance.getAnglesAndElevs("resources/ATL/00026AD_reg.txt");
        instance.getMiscData("resources/ATL/00026AD_reg.txt");
        System.out.println("City, State: " + instance.city_state + "\n" +
                            "Airport: " + instance.airport + "\n" +
                            "Variation: " + instance.variation);
        for (int i = 0; i < instance.angles.size(); i++)
        {
            System.out.println("Runway: " + instance.runways.get(i) + "\n" +
                               "Angle: " + instance.angles.get(i) + "\n" +
                               "Elev: " + instance.elevs.get(i));
        }
    }
}

