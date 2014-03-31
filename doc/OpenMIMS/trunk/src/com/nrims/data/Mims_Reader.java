package com.nrims.data;

import java.io.*;
import java.text.DecimalFormat;
import com.nrims.common.*;
import ij.ImagePlus;
import ij.io.FileInfo;
import ij.io.FileOpener;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Class responsible for opening MIMS files.
 */
public class Mims_Reader implements Opener {

    /**
     * The number of bits per image pixel.  This is currently always 16.
     */
    //BITS_PER_PIXEL appeared nowhere ???  and why not bytes?
    public static final int BITS_PER_PIXEL = 16;
    public int bytes_per_pixel = 2;

    private File file = null;
    private RandomAccessEndianFile in;
    private MimsFileInfo fi;
    private int verbose = 0;
    private int nMasses = 0;
    private HeaderImage ihdr;
    private DefAnalysis dhdr;
    private MaskSampleStageImage maskSampleStageIm;
    private MaskImage maskIm;
    private int currentIndex = 0;
    public static final int IHDR_SIZE = 84;
    private String[] massNames;
    private String[] massSymbols;
    private double counting_time;
    private String notes = "";
    private boolean isDTCorrected = false;
    private boolean isQSACorrected = false;
    private float[] betas;
    private float fc_objective;
    private boolean isPrototype = false;
    private String[] tilePositions = null;
    private HashMap metaData = new HashMap();

    // These field were specifically selected by analysts as having importance.
    // Rather than create a class for the structure, we just want the field.
    //
    // If we find ourselves needing more fields from the structure than we
    // may want to create equivalent classes for those structures, similar to
    // what is done with HeaderImage, DefAnalysis, MaskSampleStageImage and MaskImage.
    //
    // We store as Strings becasue theat is how they are stored in the NRRD file,
    // how they are read by NrrdReader, and there is no need to keep original data type.
    private String BField;          // Magnetic field in DAC
    private String PrimCurrentT0;   // Primary current at t = 0 in pA
    private String PrimCurrentTEnd; // Primary current at t = End in pA
    private String D1Pos;           // D1 position
    private String pszComment;      // Comment
    private String Radius;	         // Trolley radius
    private String ESPos;           // Entrance slit position
    private String ASPos;           // Aperture slit position
    
    /* flag describing the byte order in file */
    private boolean big_endian_flag = true;

    /* file type for MIMS .im images */
    private static final int MIMS_IMAGE = 27;
    private static final int MIMS_LINE_SCAN_IMAGE = 39;
    private static final int MIMS_SAMPLE_STAGE_IMAGE = 41;

    private Mims_Reader() {}

    /**
     * Opens and reads a SIMS image from an image file.
     * @param imageFile file of the image to be loaded.
     * @throws IOException if there is a problem reading the image file
     * @throws NullPointerException if the imagename is null or empty.
     */
    public Mims_Reader(File imageFile) throws IOException, NullPointerException {

        this.file = imageFile;
        if (!file.exists())
            throw new NullPointerException("File " + imageFile + " does not exist.");

        // Read the header.
        try {
           fi = getHeaderInfo();
        } catch (IOException io) {
           io.printStackTrace();
        }

    }

    // The image file which this Opener is interfacing.
    public File getImageFile() {
        return file;
    }

    // Reads a Char.
    final String getChar(int n) throws IOException {
        String rstr = new String();
        byte[] buf = new byte[n];
        int nr = in.read(buf);
        for (int i = 0; i < n && buf[i] != 0; i++) {
            rstr += (char) buf[i];
        }
        return rstr;
    }

    /**
     * Ensures that the given index is valid (e.g. if it's >= 0 and <= nMasses).
     * @param index image mass index.
     * @throws IndexOutOfBoundsException if the given image mass index is invalid.
     */
    private void checkMassIndex(int index) {
        if (fi.nMasses <= 0) {
            throw new IndexOutOfBoundsException("No images loaded, so mass index <" + index + "> is invalid.");
        } else if (index < 0 || index >= fi.nMasses) {
            throw new IndexOutOfBoundsException("Given mass index <" + index + "> is out of range of the total number of masses <" + fi.nMasses + ">.");
        }
    }

    /**
     * Ensures that the given index is valid (e.g. if it's >= 0 and <= nImages).
     * @param index image index.
     * @throws IndexOutOfBoundsException if the given image index is invalid.
     */
    private void checkImageIndex(int index) {
        if (fi.nImages <= 0) {
            throw new IndexOutOfBoundsException("No images loaded, so image index <" + index + "> is invalid.");
        } else if (index < 0 || index >= fi.nImages) {
            throw new IndexOutOfBoundsException("Given image index <" + index + "> is out of range of the total number of imags <" + fi.nImages + ">.");
        }
    }

    /**
     * Reads the pixel data for currentIndex (plane number) from the given mass image index.
     * @param index image mass index.
     * @throws IndexOutOfBoundsException if the given image mass index is invalid.
     * @throws IOException If there is an error reading in the pixel data.
     */
    public Object getPixels(int index) throws IndexOutOfBoundsException, IOException {
      
       checkMassIndex(index);
       
       // Set up a temporary header to read the pixels from the file.
       MimsFileInfo fi_clone = (MimsFileInfo)fi.clone();

       int pixelsPerImage = fi_clone.width * fi_clone.height;
       int bytesPerMass = pixelsPerImage * bytes_per_pixel;
       
       // Calculate offset
       long offset = (long)dhdr.header_size + (long)currentIndex * (long)fi_clone.nMasses * (long)bytesPerMass;
       if (index > 0)
          offset += index * bytesPerMass;
             
       fi_clone.longOffset = offset;
       fi_clone.nImages = 1; // only going to read 1 image.
       FileOpener fo = new FileOpener(fi_clone);

       // Get image from file.
       ImagePlus imp = fo.open(false);
       if (imp == null) {          
          throw new IOException();
       }

       Object pixels;
       if (fi_clone.fileType == FileInfo.GRAY16_UNSIGNED)
          pixels = (short[])imp.getProcessor().getPixels();
       else if (fi_clone.fileType == FileInfo.GRAY32_UNSIGNED)
          pixels = (float[])imp.getProcessor().getPixels();
       else
          pixels = null;

       return pixels;
    }

    private void guessEndianOrder()
            throws FileNotFoundException, IOException
    {
        File tmp_file = getImageFile();

        if ( tmp_file == null )
        {
            return;
        }

        RandomAccessFile tmp_in = new RandomAccessFile( tmp_file, "r");

        int tmp_int = tmp_in.readInt(); /* Skipping the first 4 bytes */

        tmp_int = tmp_in.readInt();

        if ( tmp_int == MIMS_IMAGE ||
             tmp_int == MIMS_LINE_SCAN_IMAGE ||
             tmp_int == MIMS_SAMPLE_STAGE_IMAGE ) {
            fi.intelByteOrder = false;
            big_endian_flag = true;
        } else if ( DataUtilities.intReverseByteOrder( tmp_int ) == MIMS_IMAGE ||
                    DataUtilities.intReverseByteOrder( tmp_int ) == MIMS_LINE_SCAN_IMAGE ||
                    DataUtilities.intReverseByteOrder( tmp_int ) == MIMS_SAMPLE_STAGE_IMAGE ) {
            fi.intelByteOrder = true;
            big_endian_flag = false;
        }

        tmp_in.close();
    }

    /**
     * Reads the DefAnalysis structure from the SIMS file header.
     * @throws NullPointerException if the given DefAnalysis is null.
     * @throws IOException if there's an error reading in the DefAnalysis.
     */
    private void readDefAnalysis(DefAnalysis dhdr) throws NullPointerException, IOException {
        if (dhdr == null) {
            throw new NullPointerException("DefAnalysis cannot be null in readDefAnalyais.");
        }

        dhdr.release = in.readIntEndian();
        dhdr.analysis_type = in.readIntEndian();
        dhdr.header_size = in.readIntEndian();
        dhdr.sample_type = in.readIntEndian();
        dhdr.data_included = in.readIntEndian();
        dhdr.sple_pos_x = in.readIntEndian();
        dhdr.sple_pos_y = in.readIntEndian();
        dhdr.analysis_name = getChar(32);
        dhdr.username = getChar(16);
        dhdr.pos_z = in.readIntEndian();
        int unused = in.readIntEndian();
        unused = in.readIntEndian();
        unused = in.readIntEndian();
        dhdr.date = getChar(16);
        dhdr.hour = getChar(16);

        if (this.verbose > 1) {
            System.out.println("readDefAnalysis OK");
            System.out.println("dhdr.release:" + dhdr.release);
            System.out.println("dhdr.analysis_type:" + dhdr.analysis_type);
            System.out.println("dhdr.header_size:" + dhdr.header_size);
            System.out.println("dhdr.sample_type:" + dhdr.sample_type);
            System.out.println("dhdr.data_included:" + dhdr.data_included);
            System.out.println("dhdr.sple_pos_x:" + dhdr.sple_pos_x);
            System.out.println("dhdr.sple_pos_y:" + dhdr.sple_pos_y);
            System.out.println("dhdr.analysis_name:" + dhdr.analysis_name);
            System.out.println("dhdr.username:" + dhdr.username);
            System.out.println("dhdr.sample_name:" + dhdr.sample_name);
            System.out.println("dhdr.date:" + dhdr.date);
            System.out.println("dhdr.hour:" + dhdr.hour);
        }
    }

    /**
     * Reads the AutoCal structure from the SIMS file header.
     */
    private void readAutoCal(AutoCal ac) throws IOException {
        ac.mass = getChar(64);
        ac.begin = in.readIntEndian();
        ac.period = in.readIntEndian();
    }

    /**
     * Reads a Table element structure from the SIMS file header.
     */
    private void readTabelts(Tabelts te) throws IOException {

       // It appears that this structure does not follow
       // the endian-ness of the other structures in the file.
       // Keeping code as is because values not important for now.
        te.num_elt = in.readIntEndian();
        te.num_isotop = in.readIntEndian();
        te.quantity = in.readIntEndian();
    }

    /**
     * Reads a PolyAtomic element/structure from the SIMS file header.
     */
    private void readPolyAtomic(PolyAtomic pa) throws IOException {
        pa.flag_numeric = in.readIntEndian();
        pa.numeric_value = in.readIntEndian();
        pa.nb_elts = in.readIntEndian();
        pa.nb_charges = in.readIntEndian();
        pa.charge = getChar(1);
        pa.massLabel = getChar(64); //this is the string name of the mass
        pa.tabelts = new Tabelts[5];
        for (int i = 0; i < 5; i++) {
            pa.tabelts[i] = new Tabelts();
            readTabelts(pa.tabelts[i]);
        }
        // NOT IN SPEC!!!
        String unused = getChar(3);
    }

    /**
     * Reads a SigRef element/structure from the SIMS file header.
     */
    private void readSigRef(SigRef sr) throws IOException {
        sr.polyatomic = new PolyAtomic();
        readPolyAtomic(sr.polyatomic);
        sr.detector = in.readIntEndian();
        sr.offset = in.readIntEndian();
        sr.quantity = in.readIntEndian();
    }

    /**
     * Reads and returns a Mask_Iss structure from the SIMS file header.
     */
    private void readMaskIss(MaskSampleStageImage mask) throws NullPointerException, IOException {
        if (mask == null) {
            throw new NullPointerException("MaskSampleStageImage cannot be null in readMaskIm.");
        }

        mask.filename = getChar(16);
        mask.analysis_duration = in.readIntEndian();
        mask.type = in.readIntEndian();
        mask.nb_zones = in.readIntEndian();
        mask.step_unit_x = in.readIntEndian();
        mask.step_unit_y = in.readIntEndian();
        mask.step_reel_d = in.readIntEndian();
        mask.wt_int_zones = in.readDoubleEndian();
        mask.nNbCycle = in.readIntEndian();
        mask.beam_blanking = in.readIntEndian();
        mask.pulverisation = in.readIntEndian();
        mask.pulve_duration = in.readIntEndian();
        mask.auto_cal_in_anal = in.readIntEndian();
        int unused = in.readIntEndian();

        if (this.verbose > 2) {
            System.out.println(mask.getInfo());
        }

        mask.autocal = new AutoCal();
        readAutoCal(mask.autocal);

        mask.hv_sple_control = in.readIntEndian();
        mask.hvcontrol = new HvControl();
        readHvControl(mask.hvcontrol);

        mask.sig_reference = in.readIntEndian();
        mask.sig_ref = new SigRef();
        readSigRef(mask.sig_ref);
        nMasses = mask.nb_mass = in.readIntEndian();


        int tab_mass_ptr;
        int n_tabmasses = 20;
        for (int i = 0; i < n_tabmasses; i++) {
            tab_mass_ptr = in.readIntEndian();
        }

        // NOT IN SPEC!
        unused = in.readIntEndian();
    }

    /**
     * Reads and returns a Mask_im structure from the SIMS file header.
     */
    private void readMaskIm(MaskImage mask) throws NullPointerException, IOException {
        if (mask == null) {
            throw new NullPointerException("MaskImage cannot be null in readMaskIm.");
        }

        mask.filename = getChar(16);
        mask.analysis_duration = in.readIntEndian();
        mask.cycle_number = in.readIntEndian();
        mask.scantype = in.readIntEndian();
        mask.magnification = in.readShortEndian();
        mask.sizetype = in.readShortEndian();
        mask.size_detector = in.readShortEndian();
        short unused = in.readShortEndian();
        mask.beam_blanking = in.readIntEndian();
        mask.sputtering = in.readIntEndian();
        mask.sputtering_duration = in.readIntEndian();
        mask.auto_calib_in_analysis = in.readIntEndian();

        if (this.verbose > 2) {
            System.out.println("mask.filename:" + mask.filename);
            System.out.println("mask.analysis_duration:" +
                    mask.analysis_duration);
            System.out.println("mask.cycle_number:" + mask.cycle_number);
            System.out.println("mask.scantype:" + mask.scantype);
            System.out.println("mask.magnification:" + mask.magnification);
            System.out.println("mask.sizetype:" + mask.sizetype);
            System.out.println("mask.size_detector:" + mask.size_detector);
            System.out.println("mask.beam_blanking:" + mask.beam_blanking);
            System.out.println("mask.sputtering:" + mask.sputtering);
            System.out.println("mask.sputtering_duration:" + mask.sputtering_duration);
            System.out.println("mask.auto_calib:" + mask.auto_calib_in_analysis);
        }

        mask.autocal = new AutoCal();
        readAutoCal(mask.autocal);
        mask.sig_reference = in.readIntEndian();
        mask.sig_ref = new SigRef();
        readSigRef(mask.sig_ref);
        nMasses = mask.nb_mass = in.readIntEndian();
        int tab_mass_ptr;

        // changed from tab_mass[10] to tab_mass[60] in v7 of .im file spec
        // seems like this coresponds to release=4108
        int n_tabmasses = 10;
        if(this.dhdr.release >=4108) {
            n_tabmasses = 60;
        }
        for (int i = 0; i < n_tabmasses; i++) {
            tab_mass_ptr = in.readIntEndian();
            if (this.verbose > 2) {
                System.out.println("mask.tmp:" + tab_mass_ptr);
            }
        }
    }

    /**
     * Reads and returns a HvcControl structure from the SIMS file header.
     * @throws NullPointerException if the given TabMass is null.
     * @throws IOException if the TabMass cannot be read in.
     */
    private void readHvControl(HvControl hvc) throws NullPointerException, IOException {
        if (hvc == null) {
            throw new NullPointerException();
        }
        hvc.mass = getChar(64);
        hvc.debut = in.readIntEndian();
        hvc.period = in.readIntEndian();
        hvc.borne_inf = in.readDoubleEndian();
        hvc.borne_sup = in.readDoubleEndian();
        hvc.pas = in.readDoubleEndian();
        hvc.largeur_bp = in.readIntEndian();
        hvc.count_time = in.readDoubleEndian();

        // Debug output.
        if (this.verbose > 2) {
            System.out.println("HvcControl.mass:" + hvc.mass);
            System.out.println("HvcControl.debut:" + hvc.debut);
            System.out.println("HvcControl.period:" + hvc.period);
            System.out.println("HvcControl.borne_inf:" + hvc.borne_inf);
            System.out.println("HvcControl.borne_sup:" + hvc.borne_sup);
            System.out.println("HvcControl.pas:" + hvc.pas);
            System.out.println("HvcControl.largeur_bp:" + hvc.largeur_bp);
            System.out.println("HvcControl.count_time:" + hvc.count_time);
        }
    }

    /**
     * Reads and returns a TabMass structure from the SIMS file header.
     * @throws NullPointerException if the given TabMass is null.
     * @throws IOException if the TabMass cannot be read in.
     */
    private void readTabMass(TabMass tab) throws NullPointerException, IOException {
        if (tab == null) {
            throw new NullPointerException();
        }

        // One of these unused ints is NOT IN SPEC.
        // The other should be type_mass.
        int unused = in.readIntEndian();
        int unuseds2 = in.readIntEndian();
        tab.mass_amu = in.readDoubleEndian();
        tab.matrix_or_trace = in.readIntEndian();
        tab.detector = in.readIntEndian();
        tab.waiting_time = in.readDoubleEndian();
        tab.counting_time = in.readDoubleEndian();
        tab.offset = in.readIntEndian();
        tab.mag_field = in.readIntEndian();

        // Set some local variables.
        counting_time = tab.counting_time;

        // Debug output.
        if (this.verbose > 2) {
            System.out.println("TabMass.mass_amu:" + tab.mass_amu);
            System.out.println("TabMass.matrix_or_trace:" + tab.matrix_or_trace);
            System.out.println("TabMass.detector:" + tab.detector);
            System.out.println("TabMass.waiting_time:" + tab.waiting_time);
            System.out.println("TabMass.counting_time:" + tab.counting_time);
            System.out.println("TabMass.offset:" + tab.offset);
            System.out.println("TabMass.mag_field:" + tab.mag_field);
        }
        tab.polyatomic = new PolyAtomic();
        readPolyAtomic(tab.polyatomic);
    }

    /**
     * Formats a double precision to a string
     */
    private String DecimalToStr(double v, int fraction) {
     
        /*
         * Caused exceptions when the return
         * value is passed to a Double() const.
         * Because DecimalFormat uses location.
         * Switched , and . if location was non-US sometimes.
         * Which is dumb.
         */
        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        
        DecimalFormat df = new DecimalFormat("0.00", symbols);

        if (fraction != 2) {
            String format;
            format = "0";
            if (fraction > 0) {
                format += ".";
            }
            for (int i = 0; i < fraction; i++) {
                format += "0";
            }
            df.applyPattern(format);
        }
        return df.format(v);
    }

    /**
     * Reads and returns the HeaderImage structure from a SIMS image file
     * @throws NullPointerException if the given HeaderImage is null.
     * @throws IOException if there's an error reading in the HeaderImage.
     */
    private void readHeaderImage(HeaderImage ihdr) throws NullPointerException, IOException {
        if (ihdr == null) {
            throw new NullPointerException();
        }
        ihdr.size_self = in.readIntEndian();
        ihdr.type = in.readShortEndian();
        ihdr.w = in.readShortEndian();
        ihdr.h = in.readShortEndian();        
        bytes_per_pixel = ihdr.d = in.readShortEndian();
        
        // Number of masses.
        ihdr.n = in.readShortEndian();
        if (ihdr.n < 1) {
            ihdr.n = 1;
        }

        // Number of planes.
        ihdr.z = in.readShortEndian();
        ihdr.raster = in.readIntEndian();
        ihdr.nickname = getChar(64);

        if (this.verbose > 1) {
            System.out.println("readHeaderImage OK");
            System.out.println("ihdr.d:" + ihdr.d);
            System.out.println("ihdr.n:" + ihdr.n);
            System.out.println("ihdr.w:" + ihdr.w);
            System.out.println("ihdr.type:" + ihdr.type);
            System.out.println("ihdr.z:" + ihdr.z);
        }
    }

    /**
     * Reads and returns the header data from a SIMS image file
     * Creates the DefAnalysis and HeaderImage subclass's for this class.
     * @throws NullPointerExeption rethrown from sub-readers.
     * @throws IOException if there is an error reading in the header.
     */
    private MimsFileInfo getHeaderInfo() throws NullPointerException, IOException {

       // Setup file header.
       fi = new MimsFileInfo();
	    fi.directory=file.getParent(); fi.fileName=file.getName();
       fi.fileFormat = FileInfo.RAW;
       guessEndianOrder();

       // Set the connection to the image file.
       in = new RandomAccessEndianFile(file, "r");
       in.setBigEndianFlag(big_endian_flag);

       // Read the Def_Analysis structure.
       this.dhdr = new DefAnalysis();
       readDefAnalysis(dhdr);

       // Read the Mask_* data structure.
       if (dhdr.analysis_type == MIMS_IMAGE || dhdr.analysis_type == MIMS_LINE_SCAN_IMAGE) {
          maskIm = new MaskImage();
          readMaskIm(this.maskIm);
       } else if (dhdr.analysis_type == MIMS_SAMPLE_STAGE_IMAGE ) {
          maskSampleStageIm = new MaskSampleStageImage();
          readMaskIss(this.maskSampleStageIm);
       }
       if (nMasses <= 0) {
          throw new IOException("Error reading MIMS file.  Zero image masses read in.");
       }

       // Read the Tab_Mass structure and set mass names.
       massNames = new String[nMasses];
       massSymbols = new String[nMasses];
       for (int i = 0; i < nMasses; i++) {
          TabMass tm = new TabMass();
          readTabMass(tm);
          massNames[i] = DecimalToStr(tm.mass_amu, 2);
          massSymbols[i] = tm.polyatomic.massLabel.replaceAll(" ", "");
          if (massSymbols[i] == null || massSymbols[i].equals("")) {
             massSymbols[i] = "-";
          }
       }

       // Read meta data.
       if(this.dhdr.release >=4108) {
          read_metaData_v7();
       }

       // Read the Header_Image structure
       long offset = dhdr.header_size - IHDR_SIZE;
       in.seek(offset);
       this.ihdr = new HeaderImage();
       readHeaderImage(ihdr);
       fi.width = ihdr.w;
       fi.height = ihdr.h;
       fi.nImages = ihdr.z;
       fi.nMasses = ihdr.n;
       if (ihdr.d == 2) {
          fi.fileType = FileInfo.GRAY16_UNSIGNED;
       } else if (ihdr.d == 4) {
          fi.fileType = FileInfo.GRAY32_UNSIGNED;
       }

       return fi;
   }

    /*
     * Reads various fields from the header of the Cameca File Spec v7.
     */
    public void read_metaData_v7() throws NullPointerException, IOException {

       int nb_poly, nNbBField;

       // nb_poly
       long position_of_PolyList = 652 + (288*nMasses);
       long position_of_nb_poly = position_of_PolyList + (16);
       in.seek(position_of_nb_poly);
       nb_poly = in.readIntEndian();
       
       // nNbBField
       long position_of_MaskNano = 676 + (288*nMasses) + (144*nb_poly);
       long position_of_nNbBField = position_of_MaskNano + (4*24);
       in.seek(position_of_nNbBField);
       nNbBField = in.readIntEndian();
       
       // nBField
       long position_of_Tab_BField_Nano = 2228 + (288*nMasses) + (144*nb_poly);
       long position_of_nBField = position_of_Tab_BField_Nano + (4);
       in.seek(position_of_nBField);       
       int nBField = in.readIntEndian();
       BField = Integer.toString(nBField);

       // dRadius
       long position_of_Tab_Trolley_Nano = position_of_Tab_BField_Nano + (10*4) + (2*8);
       double[] dRadius = new double[12];
       for (int i = 0; i < dRadius.length; i++) {
          long position_of_dRadius = position_of_Tab_Trolley_Nano + (i*(208)) + (64+8);
          in.seek(position_of_dRadius);
          dRadius[i] = in.readDoubleEndian();
       }
       Radius = Arrays.toString(dRadius);

       // pszComment
       long position_of_Anal_param_nano = 2228 + (288*nMasses) + (144*nb_poly) + (2840*nNbBField);
       long position_of_pszComment = position_of_Anal_param_nano + (16+4+4+4+4);
       in.seek(position_of_pszComment);
       pszComment = getChar(256);
       
       // nPrimCurrentT0, nPrimCurrentTEnd
       long position_of_Anal_primary_nano = position_of_Anal_param_nano + (16+4+4+4+4+256);
       long position_of_nPrimCurrentT0 = position_of_Anal_primary_nano + (8);
       in.seek(position_of_nPrimCurrentT0);
       int nPrimCurrentT0 = in.readIntEndian();
       int nPrimCurrentTEnd = in.readIntEndian();
       PrimCurrentT0 = Integer.toString(nPrimCurrentT0);
       PrimCurrentTEnd = Integer.toString(nPrimCurrentTEnd);

       // nD1Pos
       long position_of_nD1Pos = position_of_nPrimCurrentT0 + (8+4+4+4+4+4+4+4+4);
       in.seek(position_of_nD1Pos);
       int nD1Pos = in.readIntEndian();
       D1Pos = Integer.toString(nD1Pos);

       // nESPos
       long size_Ap_primary_nano = 552;
       long position_Ap_secondary_nano = position_of_Anal_primary_nano + (size_Ap_primary_nano);
       long position_nESPos = position_Ap_secondary_nano + (8);
       in.seek(position_nESPos);
       int nESPos = in.readIntEndian();
       ESPos = Integer.toString(nESPos);

       // nASPos
       long position_nASPos = position_nESPos + (4+40+40);
       in.seek(position_nASPos);
       int nASPos = in.readIntEndian();
       ASPos = Integer.toString(nASPos);

       /*
       System.out.println("nb_poly = " + nb_poly);
       System.out.println("nNbBField = " + nNbBField);
       System.out.println("nBField = " + nBField);
       System.out.println("pszComment = " + pszComment);
       System.out.println("nPrimCurrentT0 = " + nPrimCurrentT0);
       System.out.println("nPrimCurrentTEnd = " + nPrimCurrentTEnd);
       System.out.println("nESPos = " + nESPos);
       System.out.println("nASPos = " + nASPos);
       System.out.print("dRadius = ");
       for (int i = 0; i < dRadius.length; i++) {
          System.out.print(formatter.format(dRadius[i]) + ", ");
       }
       System.out.print("\n");
       */
    }

    /**
     * @return the name of the SIMS image file
     */
    public String getName() {
        String name = file.getName();
        int extIndex = name.lastIndexOf(".im");
        return name.substring(0, extIndex);
    }

    /**
     * @return the number of planes in this SIMS image file.
     */
    public int getNImages() {
        return fi.nImages;
    }

    /**
     * @return the total number of image masses.
     */
    public int getNMasses() {
        return fi.nMasses;
    }

    /**
     * @return the width of the images in pixels.
     */
    public int getWidth() {
        return fi.width;
    }

    /**
     * @return the height of the images in pixels.
     */
    public int getHeight() {
        return fi.height;
    }

    /**
     * @return the file's data type;
     */
    public int getFileType() {
       if (bytes_per_pixel == 2)
          return FileInfo.GRAY16_UNSIGNED;
       else if(bytes_per_pixel == 4)
          return FileInfo.GRAY32_UNSIGNED;
       else
          return FileInfo.GRAY16_UNSIGNED;
    }

    /**
     * @return the IM file's header size;
     */
    public long getHeaderSize() {
       return dhdr.header_size;
    }

    /**
     * @return the file's data type;
     */
    public short getBitsPerPixel() {
       return ihdr.d;
    }

    /**
     * @param index image mass index.
     * @return a String of the mass in AMU for image at the given index.
     */
    public String getMassName(int index) {        
        return massNames[index];
    }

    public String[] getMassNames() {        
        return massNames;
    }

    /**
     * @param index image mass index.
     * @return a String of the mass symbol (eg 12C14N) for image at the given index.
     */
    public String getMassSymbol(int index) {
        return massSymbols[index];
    }

    public String[] getMassSymbols() {
        return massSymbols;
    }

    public void setDebug(int nLevel) {
        this.verbose = nLevel;
    }

    /**
     * @return the current index in a stack or multiple time point series indices are between at zero and nImages() - 1
     */
    public int getStackIndex() {
        return this.currentIndex;
    }

    /**
     * sets the current image index.
     * @param index image mass index.
     * @throws IndexOutOfBoundsException if the given index is invalid.
     */
    public void setStackIndex(int index) throws IndexOutOfBoundsException {
        checkImageIndex(index);

        if (this.currentIndex == index) {
            return;
        }
        this.currentIndex = index;
    }

    /**
     * @return the sple_pos_x,sple_pos_y entries from the SIMS image header
     */
    public String getPosition() {
        if (this.dhdr == null) {
            return null;
        }
        String pos = this.dhdr.sple_pos_x + "," + this.dhdr.sple_pos_y;
        return pos;
    }

    /**
     * @return the z position entries from the SIMS image header
     */
    public String getZPosition() {
        if (this.dhdr == null) {
            return null;
        }
        String pos_z = this.dhdr.pos_z + "";
        return pos_z;
    }

    /**
     * @return the date entry from the SIMS image header
     */
    public String getSampleDate() {
        if (this.dhdr == null) {
            return null;
        }
        if (this.dhdr.date == null) {
            return new String(" ");
        }
        return this.dhdr.date;
    }

    /**
     * @return the hour entry from the SIMS image header
     */
    public String getSampleHour() {
        if (this.dhdr == null) {
            return null;
        }
        if (this.dhdr.hour == null) {
            return new String(" ");
        }
        return this.dhdr.hour;
    }

    /**
     * @return the username entry from the SIMS image header
     */
    public String getUserName() {
        if (this.dhdr == null) {
            return null;
        }
        if (this.dhdr.sample_name == null) {
            return new String(" ");
        }
        return this.dhdr.username;
    }

    /**
     * @return the samplename entry from the SIMS image header
     */
    public String getSampleName() {
        if (this.dhdr == null) {
            return null;
        }
        if (this.dhdr.sample_name == null) {
            return new String(" ");
        }
        return this.dhdr.sample_name;
    }

    /**
     * @return the raster entry from the SIMS image header
     */
    public String getRaster() {
        if (this.ihdr == null) {
            return "";
        }
        return String.valueOf(this.ihdr.raster);
    }

    
    // @return the dwelltime per pixel in milliseconds
     
    public String getDwellTime() {
        if (this.maskIm == null || this.ihdr == null) {
            return new String(" ");
        }
        double ctime = getCountTime();
        double size = (double) (this.ihdr.w * this.ihdr.h);
        if (size == 0) {
            return new String(" ");
        }
        double dwelltime = 1000 * ctime/(size);
        String dtime = DecimalToStr(dwelltime, 3);
        return dtime;
    }
    
    public double getCountTime() {       
        return counting_time;
    }
     
    /**
     * @return the nickname from the SIMS header
     */
    public String getNickName() {
        if (this.ihdr == null) {
            return new String(" ");
        }
        return this.ihdr.nickname;
    }

    /**
     * @return the analysis_duration from the SIMS header
     */
    public double getDurationD() {
        if (this.maskIm == null) {
            return 0.0;
        }
        double duration = (double) this.maskIm.analysis_duration;
        return duration;
    }

    public String getDuration() {
        if (this.maskIm == null) {
            return null;
        }
        double duration = (double) this.maskIm.analysis_duration;
        String dtime = DecimalToStr(duration, 3);
        return dtime;
    }

    /**
     * @return the pixel Width from the SIMS header
     */
    public float getPixelWidth() {
        float pw = 1.0f;
        if (this.ihdr != null) {
            pw = (float) this.ihdr.raster / (float) this.ihdr.w;
        }
        return pw;
    }

    /**
     * @return the pixel Height from the SIMS header
     */
    public float getPixelHeight() {
        float ph = 1.0f;
        if (this.ihdr != null) {
            ph = (float) this.ihdr.raster / (float) this.ihdr.h;
        }
        return ph;
    }

    public String getNotes() {
        return this.notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public void close() {
      try {
         if (in != null)
            in.close();
      } catch (IOException ex) {
         ex.printStackTrace();
      }
    }

   /**
    * .IM files by default not corrected for deadtime.
    *
    * @return false
    */
   public boolean isDTCorrected() {
      return this.isDTCorrected;
   }

   /**
    * Set to true if dead time correction applied.
    *
    * @param isDTCorrected
    */
   public void setIsDTCorrected(boolean isDTCorrected) {
      this.isDTCorrected = isDTCorrected;
   }

   /**
    * NOT SUPPORTED. NEEDS TO BE CORRECTLY IMPLEMENTED.
    *
    * (Returns true if data is from prototype.)
    *
    * @return false
    */
   public boolean isPrototype() {
      return this.isPrototype;
   }

   /**
    * .IM files by default not QSA corrected .
    *
    * @return false
    */
   public boolean isQSACorrected() {
      return this.isQSACorrected;
   }

   /**
    * Performs a check to see if the actual file size is in agreement
    * with what the file size should be indicated by the header.
    *
    * @return <code>true</code> if in agreement, otherwise <code>false</code>.
    */
   public boolean performFileSanityCheck() {
      long header_size = getHeaderSize();
      int pixels_per_plane = getWidth() * getHeight();
      int num_planes = getNImages();
      int num_masses = getNMasses();
      int bytes = ihdr.d;

      long theoretical_file_size = (((long)pixels_per_plane)*((long)num_planes)*((long)num_masses)*((long)bytes)) + header_size;
      long file_size = file.length();

      if (theoretical_file_size == file_size)
         return true;
      else
         return false;
   }

   /**
    * Set to true if QSA correction applied.
    *
    * @param isQSACorrected
    */
   public void setIsQSACorrected(boolean isQSACorrected) {
      this.isQSACorrected = isQSACorrected;
   }

   /**
    * Set beta values for QSA correction.
    *
    * @param betas
    */
   public void setBetas(float[] betas) {
      this.betas = betas;
   }

   /**
    * Set FC Objective value for QSA correction.
    *
    * @param betas
    */
   public void setFCObjective(float fc_objective) {
      this.fc_objective = fc_objective;
   }

   /**
    * Get beta values for QSA correction.
    *
    * @return betas
    */
    public float[] getBetas() {
       return this.betas;
    }

   /**
    * Get FC Objective values for QSA correction.
    *
    * @return fc_objective
    */
    public float getFCObjective() {
       return this.fc_objective;
    }

   /**
    * Get tile name and positions.
    *
    * @return tilePositions
    */
    public String[] getTilePositions() {
       return this.tilePositions;
    }

   /**
    * .IM are strictly formatted and written by the mass spec.
    * They will never have user entered data in them.
    *
    * @return null
    */
   public HashMap getMetaDataKeyValuePairs() {
      return this.metaData;
   }

   /**
    * Set the userData HashMap.
    *
    * @param the HashMap
    */
   public void setMetaDataKeyValuePairs(HashMap metadata) {
      this.metaData = metadata;
   }

   /**
    * Sets the width (in pixels).
    *
    * @param width
    */
   public void setWidth(int width) {
      fi.width = width;
   }

   /**
    * Sets the height (in pixels).
    *
    * @param height
    */
   public void setHeight(int height) {
      fi.height = height;
   }

   /**
    * Sets the number of masses.
    *
    * @param nmasses
    */
   public void setNMasses(int nmasses) {
      fi.nMasses = nmasses;
   }

   /**
    * Sets the number of images.
    *
    * @param nimages
    */
   public void setNImages(int nimages) {
      fi.nImages = nimages;
   }

   /**
    * Sets the bits per pixels.
    *
    * @param bitsperpixel
    */
   public void setBitsPerPixel(short bitperpixel) {
      ihdr.d = bitperpixel;
   }

   /**
    * Get nBField.
    *
    * @return nBField
    */
    public String getBField() {
       return BField;
    }

   /**
    * Get nPrimCurrentT0.
    *
    * @return nPrimCurrentT0
    */
    public String getPrimCurrentT0() {
       return PrimCurrentT0;
    }

   /**
    * Get nPrimCurrentTEnd.
    *
    * @return nPrimCurrentTEnd
    */
    public String getPrimCurrentTEnd() {
       return PrimCurrentTEnd;
    }

   /**
    * Get nD1Pos
    *
    * @return nD1Pos
    */
    public String getD1Pos() {
       return D1Pos;
    }

   /**
    * Get pszComment.
    *
    * @return pszComment
    */
    public String getpszComment() {
       return pszComment;
    }

   /**
    * Get dRadius.
    *
    * @return dRadius
    */
    public String getRadius() {
       return Radius;
    }

   /**
    * Get nESPos.
    *
    * @return nESPos
    */
    public String getESPos() {
       return ESPos;
    }

   /**
    * Get nASPos.
    *
    * @return nASPos
    */
    public String getASPos() {
       return ASPos;
    }

    /*
    public String getInfo() {
        String info = "";
        try {
            if (massImages[currentMass].getPixels() == null) {
                readPixels(currentMass);
            }
            info += "MIMSFile=" + this.file.getCanonicalPath() + "\n";
            info += "Mass" + massImages[currentMass].getName() + "\n";
            info += "MinCounts=" + massImages[currentMass].getMinGL() + "\n";
            info += "MaxCounts=" + massImages[currentMass].getMaxGL() + "\n";
            info += "Mean=" + massImages[currentMass].getMeanGL() + "\n";
            info += "StdDev=" + massImages[currentMass].getStdDev() + "\n";
            int index = currentMass + 1;
            info += "MassIndex=" + index + "\n";
            info += "TotalMasses=" + getNMasses() + "\n";
            if (getNImages() > 1) {
                info += "Section=" + (currentIndex + 1) + "\n";
                info += "TotalSections=" + getNImages() + "\n";
            }
            if (tabMass[currentMass] != null) {
                info += tabMass[currentMass].getInfo();
            }
            if (ihdr != null) {
                info += ihdr.getInfo();
            }
            if (dhdr != null) {
                info += dhdr.getInfo();
            }
            if (maskIm != null) {
                info += maskIm.getInfo();
            }
            return info;
        } catch (Exception x) {
            return info;
        }
    }
    */
}

class MimsFileInfo extends FileInfo {
   public int nMasses;
}
