/*
 * UI.java
 *
 * Created on May 1, 2006, 12:59 PM
 */
package com.nrims;

import com.nrims.data.*;
import com.nrims.logging.OMLogger;
import com.nrims.managers.OpenerManager;
import com.nrims.managers.QSAcorrectionManager;
import com.nrims.managers.convertManager;
import com.nrims.unoplugin.UnoPlugin;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.io.FileSaver;
import it.sauronsoftware.junique.AlreadyLockedException;
import it.sauronsoftware.junique.JUnique;
import it.sauronsoftware.junique.MessageHandler;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.*;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * The main user interface of the NRIMS ImageJ plugin.
 * A multi-tabbed window with a file menu, the UI class
 * serves as the central hub for all classes involved
 * with the user interface.
 */
public class UI extends PlugInJFrame implements WindowListener, MimsUpdateListener, PropertyChangeListener {

    public static final long serialVersionUID = 1;
    public static final String NRRD_EXTENSION = ".nrrd";
    public static final String NRRD_HEADER_EXTENSION = ".nhdr";
    public static final String MIMS_EXTENSION = ".im";
    public static final String ROIS_EXTENSION = ".rois.zip";
    public static final String ROI_EXTENSION = ".roi";
    public static final String RATIO_EXTENSION = ".ratio";
    public static final String RATIOS_EXTENSION = ".ratios.zip";
    public static final String HSI_EXTENSION = ".hsi";
    public static final String HSIS_EXTENSION = ".hsis.zip";
    public static final String SUM_EXTENSION = ".sum";
    public static final String SUMS_EXTENSION = ".sums.zip";
    public static final String SESSIONS_EXTENSION = ".session.zip";
    public static final String ACT_EXTIONSION = ".act";

    public static final String SAVE_IMAGE = "Save Image";
    public static final String SAVE_SESSION = "Save Session";

    public int maxMasses = 25;
    private double medianFilterRadius = 1;
    
    private boolean bSyncStack = true;
    private boolean bUpdating = false;    
    private boolean currentlyOpeningImages = false;
    private boolean bCloseOldWindows = true;
    private boolean medianFilterRatios = false;
    private boolean isSum = false;
    private boolean isWindow = false;
    private int     windowRange = -1;
    private boolean isPercentTurnover = false;
    private boolean isRatio = true;
    private boolean[] bOpenMass = new boolean[maxMasses];
    private boolean silentMode = false;
    private boolean isDTCorrected = false;
    private boolean isQSACorrected = false;
    private float[] betas;
    private float fc_objective;

    private String lastFolder = null;      
    public  File   tempActionFile;                        
    private HashMap openers = new HashMap();
    private HashMap metaData = new HashMap();

    private MimsPlus[] massImages = new MimsPlus[maxMasses];
    private MimsPlus[] ratioImages = new MimsPlus[maxMasses];
    private MimsPlus[] hsiImages = new MimsPlus[maxMasses];
    private MimsPlus[] segImages = new MimsPlus[maxMasses];
    private MimsPlus[] sumImages = new MimsPlus[2 * maxMasses];
    private MimsPlus[] compImages = new MimsPlus[2 * maxMasses];

    private MimsData mimsData = null;
    private MimsLog mimsLog = null;   
    private MimsCBControl cbControl = new MimsCBControl(this);
    private MimsStackEditor mimsStackEditing = null;
    private MimsRoiManager roiManager = null;
    private MimsTomography mimsTomography = null;        
    private MimsHSIView hsiControl = null;
    private SegmentationForm segmentation = null;
    private QSAcorrectionManager qsam;
    private ReportGenerator rg = null;

    private javax.swing.JRadioButtonMenuItem[] viewMassMenuItems = null;
    private Opener image = null;
    private ij.ImageJ ijapp = null;
    private FileDrop mimsDrop;
    private HashMap wpMap = new HashMap<Double, Point>();
    private HashMap wpiMap = new HashMap<Integer, Point>();
    private HashMap hwMap = new HashMap<Double, Boolean>();
    private HashMap zmMap = new HashMap<Double, Double>();
    private HashMap hwiMap = new HashMap<Double, Boolean>();
    private HashMap zmiMap = new HashMap<Double, Double>();
    protected MimsLineProfile lineProfile;
    protected MimsAction mimsAction = null; 
    private imageNotes imgNotes;
    private PrefFrame prefs;
    private String revisionNumber = "0";
    public static Boolean single_instance_mode = false;
    public static UI ui = null;
    public boolean sessionOpened = false;
    

    // Task related variables.
    public SwingWorker task;
    boolean previousFileCanceled = true;
    
    private final static Logger OMLOGGER = OMLogger.getOMLogger(UI.class.getName());
    
    public UI() {
       this(false);
    }

    /** Creates a new instance of the OpenMIMS analysis interface.*/
    public UI(boolean silentMode) {
        //NOTE: Trying to leave strictly UI related code in here, and remove the rest
        //As such, autosaveroi is now called in NRIMS_Plugin
      super("OpenMIMS");
      
        OMLOGGER.info("");
      
        // Is this to surpress what was going to stdout, 
        // that should have been going to stderr?
        this.silentMode = silentMode;
        
        OMLOGGER.fine("Ui id = " + System.identityHashCode(this));
        OMLOGGER.fine("java.version: " + System.getProperty("java.version"));
        OMLOGGER.fine("java.vendor: " + System.getProperty("java.vendor"));
        OMLOGGER.fine("java.vendor.url: " + System.getProperty("java.vendor.url"));
        OMLOGGER.fine("java.home: " + System.getProperty("java.home"));
        OMLOGGER.fine("os.arch: " + System.getProperty("os.arch"));
        OMLOGGER.fine("os.name: " + System.getProperty("os.name"));
        OMLOGGER.fine("os.version: " + System.getProperty("os.version"));
        
        try {
            OMLOGGER.info("machine name = " + FileUtilities.getMachineName());
        } catch (Exception e) {
            OMLOGGER.info("Could not retrieve machine name");
        }
      revisionNumber = extractRevisionNumber();
      OMLOGGER.info("revisionNumber: "+revisionNumber);
      
      /*
      // Set look and feel to native OS - this has been giving us issues
      // as getting the "SystemLookAndFeel" sometimes results is very
      // different layouts for the HSIView tab. Leave commentoutr out for now.
      try {
         javax.swing.UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
         javax.swing.SwingUtilities.updateComponentTreeUI(this);
      } catch (Exception e) {
         IJ.log("Error setting native Look and Feel:\n" + e.toString());
      }
      */
      
      initComponents();
      initComponentsCustom();
      
      //read in preferences so values are gettable
      //by various tabs (ie mimsTomography, HSIView, etc.
      //when constructed further down.
      

      ijapp = IJ.getInstance();
      if (ijapp == null || (ijapp != null && !ijapp.isShowing())) {
         if (silentMode)
            ijapp = new ij.ImageJ(ij.ImageJ.NO_SHOW);
         else
            ijapp = new ij.ImageJ();         
         setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
      }
      
      prefs = new PrefFrame(this);
      
      //initialize empty image arrays
      if (image == null) {
         for (int i = 0; i < maxMasses; i++) {
            massImages[i] = null;
            ratioImages[i] = null;
            hsiImages[i] = null;
            segImages[i] = null;
         }
         for (int i = 0; i < 2 * maxMasses; i++) {
            sumImages[i] = null;
         }
      }
      //set location of window based on spawn location and size of IJ
      int xloc, yloc = 150;
      if (ijapp != null) {
         xloc = ijapp.getX();
         if (xloc + ijapp.getWidth() + this.getPreferredSize().width + 10 <
                 Toolkit.getDefaultToolkit().getScreenSize().width) {
            xloc += ijapp.getWidth() + 10;
            yloc = ijapp.getY();
         } else {
            yloc = ijapp.getY() + ijapp.getHeight() + 10;
         }
      } else {
         int screenwidth = Toolkit.getDefaultToolkit().getScreenSize().width;
         xloc = (int) (screenwidth > 832 ? screenwidth * 0.8 : screenwidth * 0.9);
         xloc -= this.getPreferredSize().width + 10;
      }
      this.setLocation(xloc, yloc);
      ij.WindowManager.addWindow(this);
      
      //create new interface for dropping files into OpenMIMS
      this.mimsDrop = new FileDrop(null, jTabbedPane1, new FileDrop.Listener() {
         public void filesDropped(File[] files) {
            if (files.length == 0) {
               IJ.error("Unable to open file. Make sure file is not from a remote location.");
               return;
            }
            if (files.length > 1) {
               IJ.error("Please drag no more than one file.");
               return;
            }
            File file = files[0];
            setLastFolder(file.getParentFile());
            setIJDefaultDir(file.getParent());
            boolean proceed = checkCurrentFileStatusBeforeOpening();
            if (proceed)
               openFileInBackground(file);
         }
      });
      
      this.ui = this;
      
      String macrosPath = IJ.getDirectory("macros");
        OMLOGGER.info("Macros filepath: " + macrosPath);
        File file = new File(macrosPath + "/openmims_tools.fiji.ijm");
        if (file.exists()) {
            OMLOGGER.info("openmims_tools.fiji.ijm found, installing");
            IJ.run("Install...", "install=" + macrosPath + "/openmims_tools.fiji.ijm");
        } else {
            OMLOGGER.info("openmims_tools.fiji.ijm NOT found.");
            //This line --probably-- broke the "open_mims" bash script called from the
            //"transfer" script...
            //IJ.error("Error: openmims_tools.fiji.ijm does not exist. Please try updating.");
        }
        
        
      //StartupScript should be DEPRICATED/REMOVED
      //Better way to initialize state is via a script
      //need to research...
      // Create and start the thread
      //Thread thread = new StartupScript(this);
      //thread.start();
      //loadMIMSFile1(new File("/nrims/home3/cpl/JASON/LOSCALZO/EXP4/110324-c3_2_1-82Se-2x999_1_2_3_4_concat.nrrd"));

      // I added this listener because for some reason,
      // neither the windowClosing() method in this class, nor the
      // windowClosing method in PlugInJFrame is registering.
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent winEvt) {
            if (IJ.getInstance() != null) {
               prefs.savePreferences();
            }
            String macrosPath = IJ.getDirectory("macros");
             File file = new File(macrosPath + "/StartupMacros.fiji.ijm");
             if (file.exists()) {
                 System.out.println("OpenMIMS: restoring StartupMacros.fiji.ijm");
                 IJ.run("Install...", "install=" + macrosPath + "/StartupMacros.fiji.ijm");
             }
            closeCurrentImage();
            close();
         }
      });
   }

    
    /**
     * Insertion status of the current MimsPlus object
     * @param mp object to be inserted.
     * @return success/failure of insertion.
     */
   public boolean addToImagesList(MimsPlus mp) {
      int i = 0; int ii = 0; boolean inserted=false; int numBefore = 0;
      while (i < maxMasses) {
          if (mp.getMimsType() == MimsPlus.RATIO_IMAGE) {
              if (ratioImages[i] == null) {
                  inserted = true;
                  if (numBefore > 0) {
                      mp.setTitle("(" + numBefore + ") " + mp.getTitle());
                  }
                  ratioImages[i] = mp;
                  getCBControl().addWindowtoList(mp);
                  getmimsTomography().resetImageNamesList();
                  return true;
              } else if (ratioImages[i].getRatioProps().equals(mp.getRatioProps())) {
                  numBefore++;
              }
              
          }
          if (mp.getMimsType() == MimsPlus.HSI_IMAGE) {
              if (hsiImages[i] == null) {
                  inserted = true;
                  if (numBefore > 0) {
                      mp.setTitle("(" + numBefore + ") " + mp.getTitle());
                  }
                  hsiImages[i] = mp;
                  getmimsTomography().resetImageNamesList();
                  return true;
              } else if (hsiImages[i].getHSIProps().equals(mp.getHSIProps())) {
                   numBefore++;
              }
          }
         if (mp.getMimsType() == MimsPlus.SEG_IMAGE && segImages[i] == null) {
            inserted = true;
            segImages[i] = mp;
            return true;
         }
         i++;         
      }     
      
      // Sum and composite images has a larger array size.
      while (ii < 2 * maxMasses) {
         if (mp.getMimsType() == MimsPlus.SUM_IMAGE) {
              if (sumImages[ii] == null) {
                  inserted = true;
                  if (numBefore > 0) {
                      mp.setTitle("(" + numBefore + ") " + mp.getTitle());
                  }
                  sumImages[ii] = mp;
                  getCBControl().addWindowtoList(mp);
                  this.mimsTomography.resetImageNamesList();
                  return true;
              } else if (sumImages[ii].getSumProps().equals(mp.getSumProps())) {
                  numBefore++;
              }
          }
          if (mp.getMimsType() == MimsPlus.COMPOSITE_IMAGE) {
              if (compImages[ii] == null) {
                  inserted = true;
                  if (numBefore > 0) {
                      mp.setTitle("(" + numBefore + ") " + mp.getTitle());
                  }
                  compImages[ii] = mp;
                  return true;
              } else if (compImages[ii].getCompositeProps().equals(mp.getCompositeProps())) {
                  numBefore++;
              }
          }
         ii++;
      }
      if (!inserted) System.out.println("Too many open images");
      return inserted;
   }

    /**
     * Closes the current image and its associated set of
     * windows if the mode is set to close open windows.
     */
    public void closeCurrentImage() {
        if (getRoiManager() != null) {
           if (getRoiManager().isVisible())
              getRoiManager().close();
        }

        if (previousFileCanceled == false)
           gatherWindowState();
        for (int i = 0; i < maxMasses; i++) {
            if (segImages[i] != null) {
                segImages[i].removeListener(this);
                if (bCloseOldWindows) {
                    if (segImages[i].getWindow() != null) {
                        segImages[i].getWindow().close();                        
                    }
                    segImages[i] = null;
                }
            }
            if (massImages[i] != null) {
                massImages[i].removeListener(this);
                if (bCloseOldWindows) {
                    if (massImages[i].getWindow() != null) {
                        massImages[i].getWindow().close();                        
                    }
                    massImages[i] = null;
                }
            }
            bOpenMass[i] = false;
            if (hsiImages[i] != null) {
                hsiImages[i].removeListener(this);
                if (bCloseOldWindows) {
                    if (hsiImages[i].getWindow() != null) {
                        hsiImages[i].getWindow().close();                        
                    }
                    hsiImages[i] = null;
                }
            }
            if (ratioImages[i] != null) {
                ratioImages[i].removeListener(this);
                if (bCloseOldWindows) {
                    if (ratioImages[i].getWindow() != null) {
                        ratioImages[i].getWindow().close();                        
                    }
                    ratioImages[i] = null;
                }
            }
        }

        for (int i = 0; i < maxMasses * 2; i++) {
            if (sumImages[i] != null) {
                sumImages[i].removeListener(this);
                if (bCloseOldWindows) {
                    if (sumImages[i].getWindow() != null) {
                        sumImages[i].getWindow().close();                        
                    }
                    sumImages[i] = null;
                }
            }
            if (compImages[i] != null) {
                compImages[i].removeListener(this);
                if (bCloseOldWindows) {
                    if (compImages[i].getWindow() != null) {
                        compImages[i].getWindow().close();                        
                    }
                    compImages[i] = null;
                }
            }
        }
        if (image != null) {
            image.close();
        }
    }

   /**
    * Brings up the graphical pane for selecting files to be opened.
    */
   public synchronized File loadMIMSFile() {

      // Bring up file chooser.
      MimsJFileChooser fc = new MimsJFileChooser(this);
      fc.setMultiSelectionEnabled(false);
      int returnVal = fc.showOpenDialog(this);

      // Open file or return null.
      if (returnVal == JFileChooser.APPROVE_OPTION) {
         File file = fc.getSelectedFile();
         setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
         boolean opened = openFile(file);
         setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
         if (opened)
            return file;
         else
            return null;
      } else {
         return null;
      }
   }

    /**
     * Opens a MIMS file in the background. Do no call this method if your code
     * does work with the file once opened, instead call openFile.
     * @param file to be opened.
     */
    public void openFileInBackground(File file) {
        if (currentlyOpeningImages)
           return;
        currentlyOpeningImages = true;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        stopButton.setEnabled(true);
        task = new FileOpenTask(file, this);
        task.addPropertyChangeListener(this);
        task.execute();
   }

   /**
    * Opens file in a non-cancelable thread.
    *
    * @param file to be opened.
    */
   public boolean openFile(File file) {
      boolean opened;
      try {
         setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
         FileOpenTask fileOpenTask = new FileOpenTask(file, this);
         opened = fileOpenTask.doInBackground();
      } finally {
         setCursor(null);

      }
      return opened;
   }

   /**
    * gathers the state of all the mass images by filling in
    * the windows position, windows hidden, and windows zoom HashMaps.
    *
    */
   public void gatherWindowState() {

      wpMap = new HashMap<Double, Point>();
      hwMap = new HashMap<Double, Boolean>();
      zmMap = new HashMap<Double, Double>();

      if (massImages == null || massImages.length == 0) {
         return;
      }

      MimsPlus mp;
      Double mass;
      Double zoom;
      Point point;
      Boolean isVisible;
      for (int i = 0; i < massImages.length; i++) {
         mp = massImages[i];
         if (mp == null) {
            continue;
         }
         mass = mp.getMassValue();
         isVisible = mp.isVisible();
         if (mp.getWindow() != null) {
            point = mp.getWindow().getLocation();
         } else {
            point = new Point(-1, -1);
         }
         if (mp.getCanvas() != null) {
            zoom = mp.getCanvas().getMagnification();
         } else {
            zoom = 1.0;
         }
         wpMap.put(mass, point);
         wpiMap.put(i, point);
         hwMap.put(mass, isVisible);
         zmMap.put(mass, zoom);
         hwiMap.put(i, isVisible);
         zmiMap.put(i, zoom);
      }
   }

   /**
    * Everybody has a different opinion on how this method should work.
    * Im sure it will change again, butthis is how it works for now:
    *
    * 1) All mass images are placed in the same location as their
    *    equvalent mass image from the previous session.
    *
    * 2) All mass images that were hidden in the previous session are
    *    hidden in the current session.
    *
    * 3) All zoom levels from the previous session are applied
    *    to the equivalent mass image of the current session.
    *
    * Negatives of this model are as follows:
    *
    * - Images that did not exist in the previous session are
    *   not tiled, and simply overlap each other in the current session.
    *
    * - Images that were hidden in the previous session are hidden in the
    *   current session. But when those images are made visible (e.g. View > m12.00),
    *   they are placed in the upper left corner.
    */
   public void applyWindowState() {

      Double zoom;
      Point point;
      Boolean isVisible;
      double massVal;
      for (int i = 0; i < massImages.length; i++) {
         if (massImages[i] != null) {
            massVal = massImages[i].getMassValue();
            point = getWindowLocationIdx(i);
            isVisible = getWindowVisibleIdx(i);
            zoom = getWindowZoomIdx(i);
            
            if (point !=  null) {
               massImages[i].getWindow().setLocation(point);
            }

            if (isVisible == false) {
               massImages[i].hide();
            }

            if (zoom != 1.0) {
               applyZoom(massImages[i], zoom);
            }

         }
      }
   }

   /**
    * Gets the window position of the mass image
    * with value <code>massVal1</code> from the previous session.
    *
    * @param massVal1 the mass value.
    * @return the position.
    */
    public Point getWindowLocation(double massVal1) {
       Double massVal2;
       for (Object key : wpMap.keySet()) {
          massVal2 = (Double) key;
          double diff = Math.abs(massVal2 - massVal1);
          if (diff < 0.5)
             return (Point)wpMap.get(key);
       }
       return null;
    }
    /**
    * Gets the window position of the mass image
    * with value <code>massValIdx</code> from the previous session.
    *
    * @param massValIdx the mass value.
    * @return the position.
    */
    public Point getWindowLocationIdx(Integer massValIdx) {
       return (Point)wpiMap.get(massValIdx);
    }

   /**
    * Gets the window visibility of the mass image
    * with value <code>massVal1</code> from the previous session.
    *
    * @param massVal1 the mass value.
    * @return the visibility.
    */
    public Boolean getWindowVisible(double massVal1) {
       Double massVal2;
       for (Object key : hwMap.keySet()) {
          massVal2 = (Double) key;
          double diff = Math.abs(massVal2 - massVal1);
          if (diff < 0.5)
             return (Boolean)hwMap.get(key);
       }
       return true;
    }
    /**
    * Gets the window visibility of the mass image
    * with value <code>massVal1</code> from the previous session.
    *
    * @param massVal1 the mass index value.
    * @return the visibility.
    */
    public Boolean getWindowVisibleIdx(Integer massVal1) {
       return (Boolean)hwiMap.get(massVal1);
    }

   /**
    * Gets the window zoom level of the mass image
    * with value <code>massVal1</code> from the previous session.
    *
    * @param massVal1 the mass value.
    * @return the zoom level.
    */
    public Double getWindowZoom(double massVal1) {
       Double massVal2;
       for (Object key : zmMap.keySet()) {
          massVal2 = (Double) key;
          double diff = Math.abs(massVal2 - massVal1);
          if (diff < 0.5)
             return (Double)zmMap.get(key);
       }
       return 1.0;
    }
    /**
    * Gets the window zoom level of the mass image
    * with value <code>massVal1</code> from the previous session.
    *
    * @param massVal1 the mass value.
    * @return the zoom level.
    */
    public Double getWindowZoomIdx(Integer massVal1) {
       return (Double)zmiMap.get(massVal1);
    }

    /**
     * Applies the magnification factor stored <code>zoom</code>
     * to the specified image <code>mp</code>
     *
     */
    public void applyZoom(MimsPlus mp, double zoom) {

       if (zoom == 0.0 || zoom == 1.0)
          return;
      
       if (mp == null)
          return;

       if (mp.getCanvas() == null)
          return;

       if (mp.getCanvas().getMagnification() < zoom) {
          while (mp.getCanvas().getMagnification() < zoom) {
             mp.getCanvas().zoomIn(0, 0);
          }
       } else if (mp.getCanvas().getMagnification() > zoom) {
          while (mp.getCanvas().getMagnification() > zoom) {
             mp.getCanvas().zoomOut(0, 0);
          }
       }
    }

   public void removeComponentFromMimsDrop(Component comp) {
      if (comp != null)
         mimsDrop.remove(comp);
   }

    /**
    * Resets the "view" menu item to reflect the
    * mass images in the current data file.
    */
   private void resetViewMenu() {

      if (isSilentMode())
         return;

      for (int i = 0; i < viewMassMenuItems.length; i++) {
         if (i < image.getNMasses()) {
            viewMassMenuItems[i].setText(image.getMassNames()[i]);
            viewMassMenuItems[i].setVisible(true);
            double massVal = massImages[i].getMassValue();
            Boolean isVisible = getWindowVisible(massVal);
            if (isVisible) {
               viewMassMenuItems[i].setSelected(true);
            } else {
               viewMassMenuItems[i].setSelected(false);
            }
         } else {
            viewMassMenuItems[i].setText("foo");
            viewMassMenuItems[i].setVisible(false);
            viewMassMenuItems[i].setSelected(false);
         }
      }
   }

    /** Initializes the view menu.*/
    private void initializeViewMenu() {

        this.viewMassMenuItems = new javax.swing.JRadioButtonMenuItem[this.maxMasses];

        for (int i = 0; i < viewMassMenuItems.length; i++) {
            javax.swing.JRadioButtonMenuItem massRButton = new javax.swing.JRadioButtonMenuItem();

            if (i < image.getNMasses()) {
                massRButton.setVisible(true);
                massRButton.setSelected(true);
                massRButton.setText(image.getMassNames()[i]);
            } else {
                massRButton.setSelected(false);
                massRButton.setText("foo");
                massRButton.setVisible(false);
            }

            massRButton.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(java.awt.event.ActionEvent evt) {
                    viewMassChanged(evt);
                }
            });
            viewMassMenuItems[i] = massRButton;

            this.viewMenu.add(massRButton);
        }

    }

    /** Action method for the "view" menu items. */
    private void viewMassChanged(java.awt.event.ActionEvent evt) {
        int index = 0;
        for (int i = 0; i < viewMassMenuItems.length; i++) {
            if (evt.getActionCommand() == viewMassMenuItems[i].getText()) {
                index = i;
                break;
            }
        }

        if (massImages[index] == null)
           return;

        MimsPlus mp = massImages[index];

         if (viewMassMenuItems[index].isSelected() && !mp.isVisible()) {
            Double mass = mp.getMassValue();
            Point point = getWindowLocation(mass);            
            mp.show();
            mp.getWindow().setLocation(point);
            mp.setbIgnoreClose(true);
         } else if( !viewMassMenuItems[index].isSelected() && mp.isVisible()) {
            Double mass = mp.getMassValue();
            Point point = mp.getWindow().getLocation();
            wpMap.put(mass, point);
            wpiMap.put(index, point);
            mp.hide();
         }
    }

    /** 
     * The behavior for "closing" mass images, when closing is not allowed.
     */
    public void massImageClosed(MimsPlus im) {
        for (int i = 0; i < massImages.length; i++) {
            if (massImages[i] != null) {
                if (massImages[i].equals(im)) {
                    viewMassMenuItems[i].setSelected(false);
                }
            }
        }
    }

    /**
     * Returns the index of the ratio image with numerator
     * <code>numIndex</code> and denominator <code>denIndex</code>.
     *
     * @param numIndex numerator mass index.
     * @param denIndex denominator mass index.
     * @return index of ratio image, -1 of none exists.
     */
    public int getRatioImageIndex(int numIndex, int denIndex) {        
        for (int i = 0; i < ratioImages.length; i++) {
           if (ratioImages[i] != null) {
               RatioProps rp = ratioImages[i].getRatioProps();
               if (rp.getNumMassIdx() == numIndex && rp.getDenMassIdx() == denIndex) {
                  return i;
               }
           }
        }
        return -1;
    }

    /**
     * Returns the index of the HSI image with numerator
     * <code>numIndex</code> and denominator <code>denIndex</code>.
     *
     * @param numIndex numerator mass index.
     * @param denIndex denominator mass index.
     * @return index of ratio image, -1 of none exists.
     */
    public int getHsiImageIndex(int numIndex, int denIndex) {
        for (int i = 0; i < hsiImages.length; i++) {
           if (hsiImages[i] != null) {
               HSIProps hp = hsiImages[i].getHSIProps();
               if (hp.getNumMassIdx() == numIndex && hp.getDenMassIdx() == denIndex) {
                  return i;
               }
           }
        }
        return -1;
    }

    /**
     * Extracts the revision number from the file <code>buildnum.txt</code>
     *
     * @return a string containing the build number.
     */
    private String extractRevisionNumber() {
        String revision = "";
        try {
            InputStream build = getClass().getResourceAsStream("/buildnum.txt");
            InputStreamReader buildr = new InputStreamReader(build);
            BufferedReader br = new BufferedReader(buildr);            
            revision = br.readLine();
            if (revision.contains(":")) {
                revision = revision.split(":")[1].trim();
            }
            br.close();
            buildr.close();
            build.close();            
        } catch (Exception v) {
            //v.printStackTrace();
        }
        return revision;
    }


    // TODO: Fix Me
    /**
     * Opens a segmented image.
     *
     * @param segImage the data array (width x height x numPlanes).
     * @param description a simple description to be used as a title.
     * @param segImageHeight the height of the image in pixels.
     * @param segImageWidth the width of the image in pixels.
     */
    public void openSeg(int[] segImage, String description, int segImageHeight, int segImageWidth) {

        int npixels = segImageWidth * segImageHeight;
        if (segImage.length % npixels != 0) return;
        int nplanes = (int) Math.floor(segImage.length / npixels);

        //TODO: need to unify these, ie fix the multi-plane part
        if (nplanes > 1) {
            ImageStack stack = new ImageStack(segImageWidth, segImageHeight, nplanes);

            for (int offset = 0; offset < nplanes; offset++) {
                int[] pixels = new int[npixels];
                for (int i = 0; i < npixels; i++) {
                    pixels[i] = segImage[i + (npixels * offset)];
                }
                stack.setPixels(pixels, offset + 1);

            }
            ImagePlus img = new ImagePlus("seg", stack);
            img.show();
        } else {
            MimsPlus mp = new MimsPlus(this, segImageWidth, segImageHeight, segImage, description);
            mp.setHSIProcessor(new HSIProcessor(mp));
            boolean bShow = (mp == null);
            // find a slot to save it
            boolean bFound = false;


            bFound = true;
            segImages[0] = mp;
            int segIndex = 0;

            if (!bFound) {
                segIndex = 5;
                segImages[segIndex] = mp;
            }

            mp.addListener(this);
            bShow = true;
            if (bShow) {
                while (mp.getHSIProcessor().isRunning()) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException x) {
                    }
                }
                mp.show();
            }
        }
    }



    /**
     * Updates all images and kills any active
     * ROIs that might be on those images.
     */
    public void updateAllImages() {
        for (int i = 0; i < maxMasses; i++) {
            if (segImages[i] != null) {
                segImages[i].updateAndDraw();
                segImages[i].killRoi();
            }
            if (massImages[i] != null) {
                massImages[i].updateAndDraw();
                massImages[i].killRoi();
            }
            if (hsiImages[i] != null) {
                hsiImages[i].updateAndDraw();
                hsiImages[i].killRoi();
            }
            if (ratioImages[i] != null) {
                ratioImages[i].updateAndDraw();
                ratioImages[i].killRoi();
            }
        }

        for (int i = 0; i < maxMasses * 2; i++) {
            if (sumImages[i] != null) {
                sumImages[i].updateAndDraw();
                sumImages[i].killRoi();
            }
            if (compImages[i] != null) {
                compImages[i].updateAndDraw();
                compImages[i].killRoi();
            }
        }
    }
    public void recomputeAllImages() {
        recomputeAllHSI();
        recomputeAllRatio();
        recomputeAllComposite();
        ArrayList<Integer> sumlist = new ArrayList<Integer>();
        for(int i = 1; i <= ui.getmimsAction().getSize(); i++)
            sumlist.add(i);
        recomputeAllSum(sumlist);
    }
    /**
     * Updates the scroll bar size/placement.
     *
     * As far as I can tell the only way I can see to do
     * update the scroll bar is to update the slice position twice.
     */
    public void updateScrollbars() {
       int current_slice = massImages[0].getCurrentSlice();
       int num_planes = massImages[0].getNSlices();
       if (current_slice == num_planes)
         massImages[0].setSlice(num_planes-1);
       else
         massImages[0].setSlice(num_planes);
       massImages[0].setSlice(current_slice);
    }

    /**
     * Recomputes all ratio images. This needs to be done
     * whenever an action is performed that changes the
     * underlying data of the ratio image. For example,
     * using a median filter.
     */
    public void recomputeAllRatio() {
        MimsPlus[] openRatio = this.getOpenRatioImages();
        for (int i = 0; i < openRatio.length; i++) {
            openRatio[i].computeRatio();
            openRatio[i].updateAndDraw();            
        }      
        //cbControl.updateHistogram();
    }

    /**
     * Recomputes all HSI images. This needs to be done
     * whenever an action is performed that changes the
     * underlying data of the HSI image. For example,
     * using a median filter.
     */
    public void recomputeAllHSI() {
        MimsPlus[] openHSI = this.getOpenHSIImages();
        for (int i = 0; i < openHSI.length; i++) {
            openHSI[i].computeHSI();
            openHSI[i].updateAndDraw();
        }        
    }                  

    /**
     * Recomputes a composite image.
     * @param img the parent image.
     */
    public void recomputeComposite(MimsPlus img) {
        MimsPlus[] openComp = this.getOpenCompositeImages();
        for (int i = 0; i < openComp.length; i++) {
            CompositeProps props = openComp[i].compProps;
            MimsPlus[] parentImgs = props.getImages();
            for (int j = 0; j < parentImgs.length; j++) {
                if(parentImgs[j]!=null) {
                    if(img.equals(parentImgs[j])) {
                        openComp[i].computeComposite();
                        openComp[i].updateAndDraw();
                    }
                }
            }
        }
    }
        /**
     * Recomputes all composite images.
     */
    public void recomputeAllComposite() {
        MimsPlus[] openComp = this.getOpenCompositeImages();
        for (int i = 0; i < openComp.length; i++) {
            openComp[i].computeComposite();
            openComp[i].updateAndDraw();
        }
    }
    /**
     * Recomputes all sum images
     * @param sumlist list of slices to add in sum
     */
    public void recomputeAllSum(ArrayList<Integer> sumlist) {
        MimsPlus[] openSum = this.getOpenSumImages();
        for (int i = 0; i < openSum.length; i++) {
            openSum[i].computeSum(sumlist);
            openSum[i].updateAndDraw();
        }
    }

    /**
     * Catch events such as changing the slice number of a stack
     * or drawing ROIs and if enabled,  update or synchronize all images.
     *
     * @param evt
     */
    @Override
    public synchronized void mimsStateChanged(MimsPlusEvent evt) {

        // Do not call updateStatus() here - causes a race condition..
        if (currentlyOpeningImages || bUpdating)
            return;
        bUpdating = true;

        // Sychronize stack displays.
        if (bSyncStack && evt.getAttribute() == MimsPlusEvent.ATTR_UPDATE_SLICE) {
            MimsPlus mp[] = this.getOpenMassImages();
            MimsPlus rp[] = this.getOpenRatioImages();
            MimsPlus hsi[] = this.getOpenHSIImages();
            MimsPlus sum[] = this.getOpenSumImages();
            MimsPlus comp[] = this.getOpenCompositeImages();

            // Set mass images.
            int nSlice = evt.getSlice();
            boolean updateRatioHSI = evt.getUpdateRatioHSI();
            MimsPlus mplus = evt.getMimsPlus();
            for (int i = 0; i < mp.length; i++) {
               mp[i].setSlice(nSlice);
            }                                                    

            if (!isSum) {
                if (updateRatioHSI) {
                    if (mplus == null) {

                        // Update HSI image slice.
                        for (int i = 0; i < hsi.length; i++) {
                            hsi[i].computeHSI();
                        }

                        // Update ratio images.
                        for (int i = 0; i < rp.length; i++) {
                            rp[i].computeRatio();
                        }

                        // Update composite images.
                        for (int i = 0; i < comp.length; i++) {
                            comp[i].computeComposite();
                        }
                    } else {
                        if (mplus.getMimsType()==MimsPlus.RATIO_IMAGE)
                            mplus.computeRatio();
                        else if (mplus.getMimsType()==MimsPlus.HSI_IMAGE)
                            mplus.computeHSI();
                        else if (mplus.getMimsType()==MimsPlus.COMPOSITE_IMAGE)
                            mplus.computeComposite();
                    }
                }
                // Update rois in sum images
                // This is questionable code.
                for (int i = 0; i < sum.length; i++) {
                    // For some reason 1 does not work... any other number does.
                    sum[i].setSlice(2);
                }
            }
            
            autocontrastAllImages();
            //cbControl.updateHistogram();
            roiManager.updateSpinners();
            bUpdating = false;
            this.mimsStackEditing.resetTrueIndexLabel();
            this.mimsStackEditing.resetSpinners();
            return;

        } else if (evt.getAttribute() == MimsPlusEvent.ATTR_SET_ROI || 
                   evt.getAttribute() == MimsPlusEvent.ATTR_MOUSE_RELEASE) {
            // Update all images with a selected ROI 
            // MOUSE_RELEASE catches drawing new ROIs             
                int i;
                if(evt.getRoi()!= null) evt.getRoi().setStrokeColor(Color.yellow);  // needed to highlight current ROI on all images
                                                                                    // previous code did not highlight ShapeRoi objects
                MimsPlus mp = (MimsPlus) evt.getSource();
                for (i = 0; i < image.getNMasses(); i++) {
                    if (massImages[i] != mp && massImages[i] != null && bOpenMass[i]) {
                        massImages[i].setRoi(evt.getRoi());
                    }
                }
                for (i = 0; i < hsiImages.length; i++) {
                    if (hsiImages[i] != mp && hsiImages[i] != null) {
                        hsiImages[i].setRoi(evt.getRoi());
                    }
                }
                for (i = 0; i < ratioImages.length; i++) {
                    if (ratioImages[i] != mp && ratioImages[i] != null) {
                        ratioImages[i].setRoi(evt.getRoi());
                    }
                }
                for (i = 0; i < segImages.length; i++) {
                    if (segImages[i] != mp && segImages[i] != null) {
                        segImages[i].setRoi(evt.getRoi());
                    }
                }
                for (i = 0; i < sumImages.length; i++) {
                    if (sumImages[i] != mp && sumImages[i] != null) {
                        sumImages[i].setRoi(evt.getRoi());
                    }
                }
                for (i = 0; i < compImages.length; i++) {
                    if (compImages[i] != mp && compImages[i] != null) {
                        compImages[i].setRoi(evt.getRoi());
                    }
                }
            // Automatically appends a drawn ROI to the RoiManager
            // to improve work flow without extra mouse actions.             
            if (evt.getAttribute() == MimsPlusEvent.ATTR_MOUSE_RELEASE) {
                ij.gui.Roi roi = evt.getRoi();
                if (roi != null && roi.getState() != Roi.CONSTRUCTING) {
                    MimsRoiManager rm = getRoiManager();
                    rm.add();
                    if (rm.isVisible() == false)
                       rm.showFrame();
                }
            }

        } else if (evt.getAttribute() == MimsPlusEvent.ATTR_ROI_MOVED) {
            MimsRoiManager rm = getRoiManager();
            Roi roi = evt.getRoi();

            // Lines have to be treated specially.
            if(roi.isLine()) {
               rm.moveLine(rm.getRoiByName(roi.getName()), roi);
            }

            rm.move();
        }

        bUpdating = false;
        this.mimsStackEditing.resetTrueIndexLabel();
        this.mimsStackEditing.resetSpinners();
    }

    /**
     * Determines the bahavior when an image is closed. Essentially
     * a bookkeepping method that sets certain member variable to
     * null when corresponding window is closed.
     *
     * @param mp the image being closed.
     */
   public void imageClosed(MimsPlus mp) {
            int i;
            // TODO: add switch case statement
            for (i = 0; i < sumImages.length; i++) {
               if (sumImages[i] != null)
                  if (sumImages[i].equals(mp))
                     sumImages[i] = null;
            }
            for (i = 0; i < segImages.length; i++) {
               if (segImages[i] != null)
                  if (segImages[i].equals(mp))
                     segImages[i] = null;
                
            }
            for (i = 0; i < hsiImages.length; i++) {
               if (hsiImages[i] != null)
                  if (hsiImages[i].equals(mp))
                     hsiImages[i] = null;
                
            }
            for (i = 0; i < ratioImages.length; i++) {
               if (ratioImages[i] != null)
                  if (ratioImages[i].equals(mp))
                    ratioImages[i] = null;                
            }
            for (i = 0; i < compImages.length; i++) {
               if (compImages[i] != null)
                  if (compImages[i].equals(mp))
                    compImages[i] = null;
            }
   }

   /**
    * Returns the prefix of the current data files name.
    * For example: test_file.im = test_file
    *
    * @return prefix file name.
    */
   public String getImageFilePrefix() {
       if (image != null){
            String filename = image.getImageFile().getName().toString();
            String prefix = filename.substring(0, filename.lastIndexOf("."));
            return prefix;
       }else{
            return "none";
       }
   }

   /**
    * Returns the prefix of any file name.
    * For example: /tmp/test_file.im = /tmp/test_file
    *
    * @return prefix file name.
    */
   public String getFilePrefix(String fileName) {
      String prefix = fileName.substring(0, fileName.lastIndexOf("."));
      return prefix;
   }

   /** Custom actions to be called AFTER initComponent. */
   private void initComponentsCustom() {
       this.imgNotes = new imageNotes();
       this.imgNotes.setVisible(false);
       this.testingMenu.setVisible(false);
   }

   public void initComponentsTesting() {
       this.testingMenu.setVisible(true);
   }
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jMenuItem9 = new javax.swing.JMenuItem();
        jPopupMenu1 = new javax.swing.JPopupMenu();
        jMenuItem2 = new javax.swing.JMenuItem();
        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        stopButton = new javax.swing.JButton();
        jProgressBar1 = new javax.swing.JProgressBar();
        jMenuBar1 = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        openNewMenuItem = new javax.swing.JMenuItem();
        openNextMenuItem = new javax.swing.JMenuItem();
        jMenuItem1 = new javax.swing.JMenuItem();
        saveMIMSjMenuItem = new javax.swing.JMenuItem();
        jSeparator7 = new javax.swing.JSeparator();
        aboutMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JSeparator();
        exitMenuItem = new javax.swing.JMenuItem();
        editMenu = new javax.swing.JMenu();
        preferencesMenuItem = new javax.swing.JMenuItem();
        restoreMimsMenuItem = new javax.swing.JMenuItem();
        viewMenu = new javax.swing.JMenu();
        tileWindowsMenuItem = new javax.swing.JMenuItem();
        jSeparator6 = new javax.swing.JSeparator();
        roiManagerMenuItem = new javax.swing.JMenuItem();
        jSeparator8 = new javax.swing.JSeparator();
        utilitiesMenu = new javax.swing.JMenu();
        generateReportMenuItem = new javax.swing.JMenuItem();
        jMenu1 = new javax.swing.JMenu();
        openNewWriter = new javax.swing.JMenuItem();
        openNewDraw = new javax.swing.JMenuItem();
        openNewImpress = new javax.swing.JMenuItem();
        insertPicFrame = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JSeparator();
        sumAllMenuItem = new javax.swing.JMenuItem();
        importIMListMenuItem = new javax.swing.JMenuItem();
        captureImageMenuItem = new javax.swing.JMenuItem();
        imageNotesMenuItem = new javax.swing.JMenuItem();
        RecomputeAllMenuItem = new javax.swing.JMenuItem();
        jSeparator3 = new javax.swing.JSeparator();
        batch2nrrdMenuItem = new javax.swing.JMenuItem();
        exportjMenu = new javax.swing.JMenu();
        exportPNGjMenuItem = new javax.swing.JMenuItem();
        exportQVisMenuItem = new javax.swing.JMenuItem();
        closeMenu = new javax.swing.JMenu();
        closeAllRatioMenuItem = new javax.swing.JMenuItem();
        closeAllHSIMenuItem = new javax.swing.JMenuItem();
        closeAllSumMenuItem = new javax.swing.JMenuItem();
        jSeparator4 = new javax.swing.JSeparator();
        genStackMenuItem = new javax.swing.JMenuItem();
        compositeMenuItem = new javax.swing.JMenuItem();
        correctionsMenu = new javax.swing.JMenu();
        DTCorrectionMenuItem = new javax.swing.JCheckBoxMenuItem();
        QSACorrectionMenuItem = new javax.swing.JCheckBoxMenuItem();
        testingMenu = new javax.swing.JMenu();
        emptyTestMenuItem = new javax.swing.JMenuItem();

        jMenuItem9.setText("Export all images");

        jMenuItem2.setText("jMenuItem2");

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        setTitle("OpenMIMS");
        setName("NRIMSUI"); // NOI18N

        jTabbedPane1.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                jTabbedPane1StateChanged(evt);
            }
        });

        jPanel1.setName("Images"); // NOI18N
        jPanel1.setPreferredSize(new java.awt.Dimension(703, 428));

        org.jdesktop.layout.GroupLayout jPanel1Layout = new org.jdesktop.layout.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 723, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(0, 422, Short.MAX_VALUE)
        );

        jTabbedPane1.addTab("Images", jPanel1);

        stopButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/com/nrims/stopsign.png"))); // NOI18N
        stopButton.setEnabled(false);
        stopButton.setIconTextGap(0);
        stopButton.setMaximumSize(new java.awt.Dimension(27, 27));
        stopButton.setMinimumSize(new java.awt.Dimension(27, 27));
        stopButton.setPreferredSize(new java.awt.Dimension(27, 27));
        stopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopButtonActionPerformed(evt);
            }
        });

        jProgressBar1.setBackground(new java.awt.Color(211, 215, 207));
        jProgressBar1.setString("");
        jProgressBar1.setStringPainted(true);

        fileMenu.setText("File");

        openNewMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.CTRL_MASK));
        openNewMenuItem.setMnemonic('o');
        openNewMenuItem.setText("Open MIMS Image");
        openNewMenuItem.setToolTipText("Open a MIMS image from an existing .im file.");
        openNewMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openMIMSImageMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openNewMenuItem);
        openNewMenuItem.getAccessibleContext().setAccessibleDescription("Open a MIMS Image");

        openNextMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_O, java.awt.event.InputEvent.SHIFT_MASK | java.awt.event.InputEvent.CTRL_MASK));
        openNextMenuItem.setText("Open Next");
        openNextMenuItem.setToolTipText("Open next MIMS image(.im/.nrrd) in folder of current image.");
        openNextMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openNextMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openNextMenuItem);

        jMenuItem1.setText(SAVE_IMAGE);
        jMenuItem1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveMIMSjMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(jMenuItem1);

        saveMIMSjMenuItem.setText(SAVE_SESSION);
        saveMIMSjMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveMIMSjMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveMIMSjMenuItem);
        fileMenu.add(jSeparator7);

        aboutMenuItem.setText("About OpenMIMS");
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(aboutMenuItem);
        fileMenu.add(jSeparator2);

        exitMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Q, java.awt.event.InputEvent.CTRL_MASK));
        exitMenuItem.setMnemonic('x');
        exitMenuItem.setText("Exit");
        exitMenuItem.setToolTipText("Quit the NRIMS Application.");
        exitMenuItem.setName("ExitMenuItem"); // NOI18N
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(exitMenuItem);

        jMenuBar1.add(fileMenu);

        editMenu.setText("Edit");

        preferencesMenuItem.setText("Preferences...");
        preferencesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                preferencesMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(preferencesMenuItem);

        restoreMimsMenuItem.setText("Restore MIMS");
        restoreMimsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                restoreMimsMenuItemActionPerformed(evt);
            }
        });
        editMenu.add(restoreMimsMenuItem);

        jMenuBar1.add(editMenu);

        viewMenu.setText("View");

        tileWindowsMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.ALT_MASK));
        tileWindowsMenuItem.setText("Tile Windows");
        tileWindowsMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tileWindowsMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(tileWindowsMenuItem);
        viewMenu.add(jSeparator6);

        roiManagerMenuItem.setText("Roi Manager");
        roiManagerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                roiManagerMenuItemActionPerformed(evt);
            }
        });
        viewMenu.add(roiManagerMenuItem);
        viewMenu.add(jSeparator8);

        jMenuBar1.add(viewMenu);

        utilitiesMenu.setText("Utilities");

        generateReportMenuItem.setText("Generate Report");
        generateReportMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                generateReportMenuItemActionPerformed(evt);
            }
        });
        utilitiesMenu.add(generateReportMenuItem);

        jMenu1.setText("LibreOffice");

        openNewWriter.setText("Open new writer doc");
        openNewWriter.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openNewWriterActionPerformed(evt);
            }
        });
        jMenu1.add(openNewWriter);

        openNewDraw.setText("Open new draw doc");
        openNewDraw.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openNewDrawActionPerformed(evt);
            }
        });
        jMenu1.add(openNewDraw);

        openNewImpress.setText("Open new impress doc");
        openNewImpress.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openNewImpressActionPerformed(evt);
            }
        });
        jMenu1.add(openNewImpress);

        insertPicFrame.setText("Insert picture frame");
        insertPicFrame.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                insertPicFrameActionPerformed(evt);
            }
        });
        jMenu1.add(insertPicFrame);

        utilitiesMenu.add(jMenu1);
        utilitiesMenu.add(jSeparator1);

        sumAllMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.ALT_MASK));
        sumAllMenuItem.setText("Sum all Open");
        sumAllMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sumAllMenuItemActionPerformed(evt);
            }
        });
        utilitiesMenu.add(sumAllMenuItem);

        importIMListMenuItem.setText("Import .im List");
        importIMListMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                importIMListMenuItemActionPerformed(evt);
            }
        });
        utilitiesMenu.add(importIMListMenuItem);

        captureImageMenuItem.setText("Capture current Image");
        captureImageMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                captureImageMenuItemActionPerformed(evt);
            }
        });
        utilitiesMenu.add(captureImageMenuItem);

        imageNotesMenuItem.setText("Image Notes");
        imageNotesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                imageNotesMenuItemActionPerformed(evt);
            }
        });
        utilitiesMenu.add(imageNotesMenuItem);

        RecomputeAllMenuItem.setText("Recompute All");
        RecomputeAllMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                RecomputeAllMenuItemActionPerformed(evt);
            }
        });
        utilitiesMenu.add(RecomputeAllMenuItem);
        utilitiesMenu.add(jSeparator3);

        batch2nrrdMenuItem.setText("Batch covert to nrrd");
        batch2nrrdMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                batch2nrrdMenuItemActionPerformed(evt);
            }
        });
        utilitiesMenu.add(batch2nrrdMenuItem);

        exportjMenu.setText("Export...");

        exportPNGjMenuItem.setText("All Derived (png)");
        exportPNGjMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportPNGjMenuItemActionPerformed(evt);
            }
        });
        exportjMenu.add(exportPNGjMenuItem);

        exportQVisMenuItem.setText("HSI image 3D (qvis)");
        exportQVisMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exportQVisMenuItemActionPerformed(evt);
            }
        });
        exportjMenu.add(exportQVisMenuItem);

        utilitiesMenu.add(exportjMenu);

        closeMenu.setText("Close...");

        closeAllRatioMenuItem.setText("Close All Ratio Images");
        closeAllRatioMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeAllRatioMenuItemActionPerformed(evt);
            }
        });
        closeMenu.add(closeAllRatioMenuItem);

        closeAllHSIMenuItem.setText("Close All HSI Images");
        closeAllHSIMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeAllHSIMenuItemActionPerformed(evt);
            }
        });
        closeMenu.add(closeAllHSIMenuItem);

        closeAllSumMenuItem.setText("Close All Sum Images");
        closeAllSumMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                closeAllSumMenuItemActionPerformed(evt);
            }
        });
        closeMenu.add(closeAllSumMenuItem);

        utilitiesMenu.add(closeMenu);
        utilitiesMenu.add(jSeparator4);

        genStackMenuItem.setText("Generate Stack");
        genStackMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                genStackMenuItemActionPerformed(evt);
            }
        });
        utilitiesMenu.add(genStackMenuItem);

        compositeMenuItem.setText("Composite");
        compositeMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                compositeMenuItemActionPerformed(evt);
            }
        });
        utilitiesMenu.add(compositeMenuItem);

        jMenuBar1.add(utilitiesMenu);

        correctionsMenu.setText("Corrections");

        DTCorrectionMenuItem.setText("Apply dead time correction");
        DTCorrectionMenuItem.setEnabled(false);
        DTCorrectionMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                DTCorrectionMenuItemActionPerformed(evt);
            }
        });
        correctionsMenu.add(DTCorrectionMenuItem);

        QSACorrectionMenuItem.setText("Apply QSA correction");
        QSACorrectionMenuItem.setEnabled(false);
        QSACorrectionMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                QSACorrectionMenuItemActionPerformed(evt);
            }
        });
        correctionsMenu.add(QSACorrectionMenuItem);

        jMenuBar1.add(correctionsMenu);

        testingMenu.setText("Testing");

        emptyTestMenuItem.setText("warp test");
        emptyTestMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                emptyTestMenuItemActionPerformed(evt);
            }
        });
        testingMenu.add(emptyTestMenuItem);

        jMenuBar1.add(testingMenu);

        setJMenuBar(jMenuBar1);

        org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(layout.createSequentialGroup()
                .addContainerGap()
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
                    .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 731, Short.MAX_VALUE)
                    .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                        .add(jProgressBar1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 698, Short.MAX_VALUE)
                        .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                        .add(stopButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
            .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                .add(jTabbedPane1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 460, Short.MAX_VALUE)
                .add(5, 5, 5)
                .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING, false)
                    .add(jProgressBar1, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .add(stopButton, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .add(16, 16, 16))
        );

        getAccessibleContext().setAccessibleDescription("NRIMS Analyais Module");

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Restores the image to its unmodified state. Undoes
     * and compression, reinserts all deleted planes, and
     * sets all translations back to zero.
     */
    private void restoreMimsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {                                           
        
        int currentSlice = massImages[0].getCurrentSlice();

        mimsStackEditing.uncompressPlanes();

        // concatenate the remaining files.
        for (int i = 1; i <= mimsAction.getSize(); i++) {
           massImages[0].setSlice(i);
           if (mimsAction.isDropped(i)) mimsStackEditing.insertSlice(i);
        }
        mimsStackEditing.untrack();

        mimsStackEditing.resetTrueIndexLabel();
        mimsStackEditing.resetSpinners();

        massImages[0].setSlice(currentSlice);
    }

    /**
     * Sets isRatio to true, meaning data shown in ratio
     * images are ratio values and NOT percent turnover.
     *
     * @param selected <code>true</code> if generating
     * ratio images with ratio values, otherwise <code>false</code>.
     */
    public void setIsRatio(boolean selected) {
       isRatio = selected;
    }

    /**
     * Returns <code>true</code> if generating ratio images
     * with ratio values and NOT percent turnover.
     *
     * @return boolean.
     */
    public boolean getIsRatio(){
       return isRatio;
    }

    /**
     * Sets isPercentTurnover to true, meaning data shown
     * in ratio images are percent turnover values and NOT
     * ratio values.
     *
     * @param selected <code>true</code> if generating
     * ratio images with percent turnover values, otherwise
     * <code>false</code>.
     */
    public void setIsPercentTurnover(boolean selected) {
       isPercentTurnover = selected;
    }

    /**
     * Returns <code>true</code> if generating ratio images
     * with percent turnover values and NOT ratio values.
     *
     * @return boolean.
     */
    public boolean getIsPercentTurnover(){
       return isPercentTurnover;
    }

    /**
     * Sets the isSum member variable to <code>set</code> telling
     * the application if ratio and HSI images should be generated
     * as sum images, instead of plane by plane.
     *
     * @param set <code>true</code> if generating ratio image
     * and HSI images as sum images, otherwise false.
     */
    public void setIsSum(boolean set) {
        isSum = set;
    }

    /**
     * Sets the DTCorrected flag.
     *
     * @param isDTCorrected
     */
    public void setIsDTCorrected(boolean isDTCorrected) {
        this.isDTCorrected = isDTCorrected;
    }

    /**
     * Sets the QSACorrected flag.
     *
     * @param isQSACorrected
     */
    public void setIsQSACorrected(boolean isQSACorrected) {
        this.isQSACorrected = isQSACorrected;
    }

    /**
     * Sets the beta QSA correction parameters.
     *
     * @param betas
     */
    public void setBetas(float[] betas) {
        this.betas = betas;
    }

    /**
     * Sets the FC Objective QSA correction parameter.
     *
     * @param fc_objective
     */
    public void setFCObjective(float fc_objective) {
        this.fc_objective = fc_objective;
    }
    
    /**
     * Returns <code>true</code> if the application is generating ratio and
     * HSI images as sum images.
     *
     * @return <code>true</code> if generating ratio images and
     * HSI images as sum images, otherwise <code>false</code>.
     */
    public boolean getIsSum() {
        return isSum;
    }

    /**
     * Sets the isWindow member vairable to <code>set</code> telling
     * the application if ratio and HSI images should be generated
     * with a window of n planes.
     *
     * @param set <code>true</code> if generating ratio and HSI
     * images with a window of <code>n</code> planes.
     */
    public void setIsWindow(boolean set) {
        isWindow = set;
    }

    /**
     * Returns <code>true</code> if the application is generating
     * ratio and HSI images with a window of <code>n</code> planes.
     *
     * @return <code>true</code> if generating ratio and HSi images
     * with a window of <code>n</code> planes, otherwise <code>false</code>.
     */
    public boolean getIsWindow() {
        return isWindow;
    }

    /**
     * Used to set the number of planes to when generating ratio
     * and HSI images with a sliding window. Actual window size
     * will be 2 times window range plus 1 (The current window is
     * currentplane - windowSize up to currentplane + windowSize).
     *
     * @param range the "radius" of the sliding window.
     */
    public void setWindowRange(int range) {
        windowRange = range;
    }

    /**
     * Returns the size of the sliding window.
     *
     * @return size of sliding window.
     */
    public int getWindowRange() {
        return windowRange;
    }

    /**
     * Tells the application to use the median filter
     * when generating ratio and HSI images. All open
     * ratio and HSI images will be regenerated.
     *
     * @param set <code>true</code> if computing ratio
     * and HSI images with a median filter, othewise <code>false</code>.
     */
    public void setMedianFilterRatios(boolean set) {
        medianFilterRatios = set;
    }

    /**
     * Returns <code>true</code> if the application is using
     * a median filter when generating ratio and HSI images.
     *
     * @return <code>true</code> if using a median filter, otherwise <code>false</code>.
     */
    public boolean getMedianFilterRatios() {
        return medianFilterRatios;
    }

    /**
     * Will perform and autocontrast on all open mass, ratio and sum images.
     */
    public void autocontrastAllImages() {       
       // All mass images             
       MimsPlus mp[] = getOpenMassImages();
        for (int i = 0; i < mp.length; i++) {
            if (mp[i].getAutoContrastAdjust())
               autoContrastImage(mp[i]);
        }
        
        // All ratio images
        MimsPlus rp[] = getOpenRatioImages();
        for (int i = 0; i < rp.length; i++) {
           if (rp[i].getAutoContrastAdjust())
              autoContrastImage(rp[i]);
        }

        // All sum images
        MimsPlus sp[] = getOpenSumImages();
        for (int i = 0; i < sp.length; i++) {
           if (sp[i].getAutoContrastAdjust())
              autoContrastImage(sp[i]);
        }
    }      

    /**
     * Autocontrasts an images according to ImageJ's autocontrasting algorithm.
     *
     * @param imgs an array of images to autocontrast.
     */
   public void autoContrastImages(MimsPlus[] imgs) {
       for(int i=0; i<imgs.length; i++) {
           if(imgs[i]!=null) {
               autoContrastImage(imgs[i]);
           }
       }
   }

   /**
    * Autocontrast an image according to ImageJ's autocontrasting algorithm.
    *
    * @param img an image to autocontrast.
    */
   public void autoContrastImage(MimsPlus img) {                 
      ContrastAdjuster ca = new ContrastAdjuster(img);
      ca.doAutoAdjust = true;
      ca.doUpdate(img);
   }

    public void logException(Exception e) {
        IJ.log(e.toString());

        StackTraceElement[] trace = e.getStackTrace();
        for (int i = 0; i < trace.length; i++) {
            IJ.log(trace[i].toString());
        }
    }
    /** An action method for the Edit>Preferences... menu item.*/
    private void preferencesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_preferencesMenuItemActionPerformed
      
       if(this.prefs==null) { prefs = new PrefFrame(this); }
       prefs.showFrame();
        
    }//GEN-LAST:event_preferencesMenuItemActionPerformed

    /** Action method for the View>Tile Windows menu item.*/
    private void tileWindowsMenuItemActionPerformed(java.awt.event.ActionEvent evt) {                                           
        //ij.plugin.WindowOrganizer wo = new ij.plugin.WindowOrganizer();
        //wo.run("tile");
      
        tileWindows();
    }

    /**
     * Saves the current analysis session. If <code>saveImageOnly</code> is
     * set to <code>true</code> than only the mass image data will be saved
     * to a file with name <code>fileName</code> (always ending with .nrrd).
     * If <code>saveImageOnly</code> is set to <code>false</code> than
     * mass image data will be save ALONG with a roi zip file containing
     * all ROIs, all ratio, HSI and sum images. All files will have the same
     * prefix name, but will slightly differ in their endings as well as
     * their extensions.
     *
     * @param fileName the name of the file to be saved.
     * @param saveImageOnly <code>true</code> if saving only the mass
     *         image data, <code>false</code> if saving Rois and derived images.
     * @return <code>true</code> if successful, otherwise <code>false</code>
     */
    public boolean saveSession(String fileName, boolean saveImageOnly) {

      // Initialize variables.
      File file = new File(fileName);
      String directory = file.getParent();
      String name = file.getName();
        String baseFileName;
        String onlyFileName;

      try {

            if (imgNotes != null) {
           getOpener().setNotes(imgNotes.getOutputFormatedText());
            }
        if (mimsAction.getIsTracked()) {
           double max_delta = mimsAction.getMaxDelta();
           DecimalFormat twoDForm = new DecimalFormat("#.##");
           insertMetaData(Opener.Max_Tracking_Delta, twoDForm.format(max_delta));
        }
            if (!metaData.isEmpty()) {
           getOpener().setMetaDataKeyValuePairs(metaData);
            }
        // Set DT correction flag.
        getOpener().setIsDTCorrected(isDTCorrected);

        // Set QSA correction flag.
        getOpener().setIsQSACorrected(isQSACorrected);

        // Set QSA correction parameters.
        if (isQSACorrected) {
           getOpener().setBetas(betas);
           getOpener().setFCObjective(fc_objective);
        }

        // Save the original .im file to a new file of the .nrrd file type.
        String nrrdFileName = name;
            if (!name.endsWith(NRRD_EXTENSION)) {
           nrrdFileName = name+NRRD_EXTENSION;        
            }

        // Save the file.
          if (saveImageOnly || ui.getmimsAction().isImageModified()) {
              ImagePlus[] imp = getOpenMassImages();
              if (imp == null) {
                  return false;
              }
              Nrrd_Writer nw = new Nrrd_Writer(this);
              File dataFile = nw.save(imp, directory, nrrdFileName);

              // Update the Opener object.
              image.close();
              image = new Nrrd_Reader(dataFile);
              openers.clear();
              openers.put(nrrdFileName, image);

                // Update the Action object.
                mimsAction = new MimsAction(image);

                // Update the Data tab
                mimsData = new MimsData(this, image);
                jTabbedPane1.setComponentAt(0, mimsData);

              // Update the image titles.
              for (int i = 0; i < imp.length; i++) {
                  imp[i].setTitle((new MimsPlus(this, i)).getTitle());
              }
              baseFileName = getFilePrefix(dataFile.getAbsolutePath());
              onlyFileName = getFilePrefix(dataFile.getName());
          } else {
              baseFileName = this.getLastFolder() + "/" + this.getImageFilePrefix();
              onlyFileName = this.getImageFilePrefix();
          }
            if (saveImageOnly) {
              return true;
              }
            //save additional images (sum, ratio, etc) and rois
            FileUtilities.saveAdditionalData(baseFileName, onlyFileName, this);
        } catch (FileNotFoundException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
            }
        return true;
            }
    /**
     * Action method for File>Open Mims Image menu item. If opening
     */
    private void openMIMSImageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {

       boolean proceed = checkCurrentFileStatusBeforeOpening();
       if (!proceed)
          return;

       // Bring up file chooser.
       MimsJFileChooser fc = new MimsJFileChooser(this);
       fc.setMultiSelectionEnabled(false);
       int returnVal = fc.showOpenDialog(this);

       // Open file or return null.
       if (returnVal == JFileChooser.APPROVE_OPTION) {
          File file = fc.getSelectedFile();
          openFileInBackground(file);
       }
    }
    /**
     * check whether the numerator mass and denominator mass referred to by indices are within the range of the ones supplied in arguments.
     * Used in restoreState to check HSI/Ratio/Sum-Ratio images indices/masses
     * Depends on no globals, can probably move to new helper class
     * @param nidx numerator index to check
     * @param didx denominator index to check
     * @param tolerance range to be considered
     * @param numMass numerator mass to check against index
     * @param denMass denominator mass to check against index
     * @return true if within range, and false if not
     */
    public boolean withinMassRange(int nidx, int didx, double tolerance, double numMass, double denMass){
        String[] names = this.getOpener().getMassNames();
        double numDiff, denDiff;
        double mindiff = Double.MAX_VALUE;
        if (nidx == -1 || didx == -1) return false;
        double nMass = Double.valueOf(names[nidx]);
        double dMass = Double.valueOf(names[didx]);
        numDiff = Math.abs(numMass - nMass);
        denDiff = Math.abs(denMass - dMass);
        if (numDiff < mindiff && numDiff < tolerance && denDiff < mindiff && denDiff < tolerance)
            return true;
        else return false;
    }
    /**
     * check whether the mass referred to by an index are within the range of one supplied in the argument.
     * Used in restoreState to check Sum-Mass Images indices/masses
     * Depends on no globals, can probably move to new helper class
     * @param nidx index to check
     * @param tolerance range to be considered
     * @param numMass mass to check against index
     * @return true if within range, and false if not
     */
    public boolean withinMassRange(int nidx, double tolerance, double numMass){
        String[] names = this.getOpener().getMassNames();
        double numDiff;
        double mindiff = Double.MAX_VALUE;
        if (nidx == -1) return false;
        double nMass = Double.valueOf(names[nidx]);
        numDiff = Math.abs(numMass - nMass);
        if (numDiff < mindiff && numDiff < tolerance)
            return true;
        else return false;
    }

    /**
     * Regenerates the ratio images, hsi images and sum images with
     * the properties specified in the arrays. Used when opening
     * a new data file and the user wishes to generate all the same
     * derived images that were open with the previous data file.
     *
     * @param rto_props array of <code>RatioProps</code> objects.
     * @param hsi_props array of <code>HSIProps</code> objects.
     * @param sum_props array of <code>SumProps</code> objects.
     * @param same_size <code>true</code> restores magnification.
     */
    public void restoreState( RatioProps[] rto_props,  HSIProps[] hsi_props, SumProps[] sum_props, boolean same_size, boolean roiManagerVisible){

       if (rto_props == null)
          rto_props = new RatioProps[0];
       
       if (hsi_props == null)
          hsi_props = new HSIProps[0];
       
       if (sum_props == null)
          sum_props = new SumProps[0];

       MimsPlus mp;
       // Generate ratio images.
       for (int i=0; i<rto_props.length; i++){
          int nidx = rto_props[i].getNumMassIdx();
          int didx = rto_props[i].getDenMassIdx();
          if (!withinMassRange(nidx, didx, 0.49, rto_props[i].getNumMassValue(), rto_props[i].getDenMassValue())){
              nidx = getClosestMassIndices(rto_props[i].getNumMassValue(), 0.49);
              didx = getClosestMassIndices(rto_props[i].getDenMassValue(), 0.49);
          }
          if (nidx == -1 || didx == -1)
             continue;
          rto_props[i].setNumMassIdx(nidx);
          rto_props[i].setDenMassIdx(didx);
          if (!same_size)
                rto_props[i].setMag(1.0);
          mp = new MimsPlus(this, rto_props[i]);
          mp.showWindow();
          mp.setDisplayRange(rto_props[i].getMinLUT(), rto_props[i].getMaxLUT());          
       }
       
       // Generate hsi images.
       for (int i=0; i<hsi_props.length; i++){
          int nidx = hsi_props[i].getNumMassIdx();
          int didx = hsi_props[i].getDenMassIdx();
          if (!withinMassRange(nidx, didx, 0.49, hsi_props[i].getNumMassValue(), hsi_props[i].getDenMassValue())){
              nidx = getClosestMassIndices(rto_props[i].getNumMassValue(), 0.49);
              didx = getClosestMassIndices(rto_props[i].getDenMassValue(), 0.49);
          }
          if (nidx == -1 || didx == -1)
             continue;
          hsi_props[i].setNumMassIdx(nidx);
          hsi_props[i].setDenMassIdx(didx);
          if (!same_size) 
             hsi_props[i].setMag(1.0);

          mp = new MimsPlus(this, hsi_props[i]);
          mp.showWindow();
       }

       // Generate sum images.
       for (int i=0; i<sum_props.length; i++){
           if (sum_props[i].getSumType() == MimsPlus.RATIO_IMAGE) {
               int nidx = sum_props[i].getNumMassIdx();
               int didx = sum_props[i].getDenMassIdx();
               if (!withinMassRange(nidx, didx, 0.49, sum_props[i].getNumMassValue(), sum_props[i].getDenMassValue())) {
                   nidx = getClosestMassIndices(rto_props[i].getNumMassValue(), 0.49);
                   didx = getClosestMassIndices(rto_props[i].getDenMassValue(), 0.49);
               }
               if (nidx == -1 || didx == -1) continue;
               hsi_props[i].setNumMassIdx(nidx);
               hsi_props[i].setDenMassIdx(didx);
               if (!same_size) sum_props[i].setMag(1.0);
               mp = new MimsPlus(this, sum_props[i], null);
               mp.showWindow();
               mp.setDisplayRange(sum_props[i].getMinLUT(), sum_props[i].getMaxLUT());

          } else if (sum_props[i].getSumType() == MimsPlus.MASS_IMAGE) {
                int pidx = sum_props[i].getParentMassIdx();
                if (pidx == -1)
                  continue;
                if (!withinMassRange(pidx, 0.49, sum_props[i].getParentMassValue())) {
                   pidx = getClosestMassIndices(sum_props[i].getParentMassValue(), 0.49);
               }
                sum_props[i].setParentMassIdx(pidx);
                if (!same_size)
                   sum_props[i].setMag(1.0);
                mp = new MimsPlus(this, sum_props[i], null);
                mp.showWindow();
                mp.setDisplayRange(sum_props[i].getMinLUT(), sum_props[i].getMaxLUT());             
          }
       }

       getRoiManager().setVisible(roiManagerVisible);
    }

    /**
     * Determines the default name to assign ratio, HSi and sum images
     * when being saved. Generally they will all have the same prefix name
     * followed by some detail about the image along with an extension that
     * corresponds to the type of image.
     * <p>
     * For example, if the current open file is called test_file.nrrd and
     * the user wished to save all derived images, the following are possible
     * names of the saved images depending on its type:
     * <ul>
     * <li>test_file_m13.sum
     * <li>test_file_m13_m12.sum
     * <li>test_file_m13_m12.ratio
     * <li>test_file_m13_m12.hsi
     * </ul>
     *
     * @param img the image to be saved.
     * @return the default name of the file.
     */
    String getExportName(MimsPlus img) {
        String name = "";
        name += this.getImageFilePrefix();

           if(img.getMimsType()==MimsPlus.MASS_IMAGE) {
               int index = img.getMassIndex();
               int mass = Math.round(new Float(getOpener().getMassNames()[index]));
               name += "_m" + mass;
               return name;
           }

        if(img.getMimsType()==MimsPlus.RATIO_IMAGE) {
            RatioProps ratioprops = img.getRatioProps();
            int numIndex = ratioprops.getNumMassIdx();
            int denIndex = ratioprops.getDenMassIdx();
            int numMass = Math.round(new Float(getOpener().getMassNames()[numIndex]));
            int denMass = Math.round(new Float(getOpener().getMassNames()[denIndex]));
            name += "_m" + numMass + "_m" + denMass + "_ratio";
            return name;
        }

        if(img.getMimsType()==MimsPlus.HSI_IMAGE) {
            HSIProps hsiprops = img.getHSIProps();

           int numIndex = hsiprops.getNumMassIdx();
           int denIndex = hsiprops.getDenMassIdx();
           int numMass = Math.round(new Float(getOpener().getMassNames()[numIndex]));
           int denMass = Math.round(new Float(getOpener().getMassNames()[denIndex]));
           name += "_m" + numMass + "_m" + denMass + "_hsi";
           return name;
        }

        if (img.getMimsType() == MimsPlus.SUM_IMAGE) {
            SumProps sumProps = img.getSumProps();
            if (sumProps.getSumType() == SumProps.RATIO_IMAGE) {
                int numIndex = sumProps.getNumMassIdx();
                int denIndex = sumProps.getDenMassIdx();
                int numMass = Math.round(new Float(getOpener().getMassNames()[numIndex]));
                int denMass = Math.round(new Float(getOpener().getMassNames()[denIndex]));
                name += "_m" + numMass + "_m" + denMass + "_sum";
                return name;
            } else if (sumProps.getSumType() == SumProps.MASS_IMAGE) {
                int parentIndex = sumProps.getParentMassIdx();
                int parentMass = Math.round(new Float(getOpener().getMassNames()[parentIndex]));
                name += "_m" + parentMass + "_sum";
                return name;
            }

        }

        if (img.getMimsType() == MimsPlus.SEG_IMAGE) {
            name += "_seg";
            return name;
        }

        if (img.getMimsType() == img.COMPOSITE_IMAGE) {
            name += "_comp";
            return name;
        }

        return name;
    }

    /**
     * Action method for changing tabs. Im not sure but I dont think
     * this should be needed. Possible future delete.
     */
    private void jTabbedPane1StateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_jTabbedPane1StateChanged

        //if (this.mimsTomography != null) {
        //    this.mimsTomography.resetImageNamesList();
        //}

        //if (this.mimsStackEditing != null) {
        //    this.mimsStackEditing.resetTrueIndexLabel();
        //    this.mimsStackEditing.resetSpinners();
        //}

    }//GEN-LAST:event_jTabbedPane1StateChanged

    /** Action method File>Exit menu item. Closes the application. */
private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
   WindowEvent wev = new WindowEvent(this, WindowEvent.WINDOW_CLOSING);
   Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(wev);
}//GEN-LAST:event_exitMenuItemActionPerformed

   /** Action method for Utilities>Sum all Open menu item. Generates sum images
    for all open mass and ration images.*/
private void sumAllMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sumAllMenuItemActionPerformed

    SumProps sumProps;
    MimsPlus[] openmass = this.getOpenMassImages();
    MimsPlus[] openratio = this.getOpenRatioImages();

    //clear all sum images
    for (int i = 0; i < maxMasses * 2; i++) {
        if (sumImages[i] != null) {
            sumImages[i].close();
            sumImages[i] = null;
        }
    }

    // Open a sum image for each mass image.
    for (int i = 0; i < openmass.length; i++) {
        try {
           sumProps = new SumProps(openmass[i].getMassIndex());
           sumProps.setXWindowLocation(openmass[i].getWindow().getLocationOnScreen().x+MimsPlus.X_OFFSET);
           sumProps.setYWindowLocation(openmass[i].getWindow().getLocationOnScreen().y+MimsPlus.Y_OFFSET);
           MimsPlus mp = new MimsPlus(this, sumProps, null);
           mp.showWindow();
        } catch (NullPointerException npe) {
           // Most likely the user tried to close a mass image hitting the X button
        }
    }

    // open a sum image for each ratio image.
    for (int i = 0; i < openratio.length; i++) {
        sumProps = new SumProps(openratio[i].getRatioProps().getNumMassIdx(), openratio[i].getRatioProps().getDenMassIdx());
        sumProps.setRatioScaleFactor(openratio[i].getRatioProps().getRatioScaleFactor());
        sumProps.setXWindowLocation(openratio[i].getWindow().getLocationOnScreen().x+MimsPlus.X_OFFSET);
        sumProps.setYWindowLocation(openratio[i].getWindow().getLocationOnScreen().y+MimsPlus.Y_OFFSET);
        MimsPlus mp = new MimsPlus(this, sumProps, null);
        mp.showWindow();
    }
}//GEN-LAST:event_sumAllMenuItemActionPerformed

/** Action method for File>About menu item. Displays basic information about the Open Mims plugins. */
private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed

    String message = "OpenMIMS v2.5 (rev: " + revisionNumber+")";
    message += "\n\n";
    message += "http://www.nrims.hms.harvard.edu/";
    message += "\n\n";
    message += "OpenMIMS is a plugin for ImageJ, a public domain, Java-based\n";
    message += "image processing program developed at the NIH by Wayne Rasband.";
    message += "\n\n";
    message += "OpenMIMS is an open source software project that is funded through \n";
    message += "the NIH/NIBIB National Resource for Imaging Mass Spectrometry. \n";
    message += "Please use the following acknowledgment and send us references to \n";
    message += "any publications, presentations, or successful funding applications \n";
    message += "that make use of OpenMIMS software:";
    message += "\n\n";
 	 message += "    \"This work was made possible in part by the OpenMIMS software \n";
 	 message += "    whose development is funded by the NIH/NIBIB National Resource \n";
 	 message += "    for Imaging Mass Spectrometry, NIH/NIBIB 5P41 EB001974-10.\"";
    message += "\n\n";
    message += "Developed by:\n Doug Benson (RIP), Collin Poczatek \n ";
    message += "Boris Epstein, Philipp Gormanns\n Stefan Reckow, ";
    message += "Zeke Kaufman, \n Farah Kashem, William Ang.";
    message += "\n\n";
    message += "OpenMIMS has modified, uses, or depends upon: \n";
    message += "    ImageJ: http://rsbweb.nih.gov/ij/\n";
    message += "    Fiji: http://fiji.sc/Fiji\n";
    message += "    TurboReg:  http://bigwww.epfl.ch/thevenaz/turboreg/ \n";
    message += "    libSVM: http://www.csie.ntu.edu.tw/~cjlin/libsvm/ \n";
    message += "    NRRD file format: http://teem.sourceforge.net/nrrd/ \n";
    message += "    nrrd plugins: http://flybrain.stanford.edu/nrrd \n";
    message += "    jFreeChart:  http://www.jfree.org/jfreechart/ \n";
    message += "    FileDrop:  http://iharder.sourceforge.net/current/java/filedrop/ \n";
    message += "    Apache Commons: http://commons.apache.org/io/ \n";
    message += "    jRTF:  http://code.google.com/p/jrtf/ \n";
    message += "    jUnique: http://www.sauronsoftware.it/projects/junique/ \n";
    message += "\n\n";
    message += "Please cite OpenMIMS or any of the above projects when applicable.";
    
    javax.swing.JFrame frame = new javax.swing.JFrame("About OpenMIMS");
    frame.setSize(500, 550);

    javax.swing.JScrollPane scroll = new javax.swing.JScrollPane();
    frame.add(scroll);
    javax.swing.JTextArea area = new javax.swing.JTextArea();
    area.setEditable(false);
    area.append(message);

    area.setColumns(20);
    area.setRows(5);
    
    scroll.setViewportView(area);
    int x = java.awt.MouseInfo.getPointerInfo().getLocation().x;
    int y =java.awt.MouseInfo.getPointerInfo().getLocation().y;
    frame.setLocation(x,y);
    frame.setVisible(true);

}//GEN-LAST:event_aboutMenuItemActionPerformed

/**
 * Action method for Utilities>Capture Current Image menu item. Generates
 * a .png screen capture for the most recently clicked image.
 */
private void captureImageMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_captureImageMenuItemActionPerformed

   // Captures the active image window and returns it as an ImagePlus.
   MimsPlus imp = (MimsPlus)ij.WindowManager.getCurrentImage();
   if (imp == null) {
       IJ.noImage();
       return;
   }

   Image img = getScreenCaptureCurrentImage();
   if (img == null)
      return;

   // Bring up JFileChooser and get file name.
   File file;
   MimsJFileChooser fc = new MimsJFileChooser(this);
   if (this.getImageFilePrefix() != null)
      fc.setSelectedFile(new File(getExportName(imp) + ".png"));
   MIMSFileFilter mff_png = new MIMSFileFilter("png");
   mff_png.setDescription("Snapshot image");
   fc.addChoosableFileFilter(mff_png);
   fc.setFileFilter(mff_png);
   int returnVal = fc.showSaveDialog(jTabbedPane1);
   if (returnVal == JFileChooser.APPROVE_OPTION) {
      String fileName = fc.getSelectedFile().getAbsolutePath();
      file = new File(fileName);
   } else {
      return;
   }

   // Save file.
   try {
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      ImagePlus imp2 = new ImagePlus(file.getName(), img);
      FileSaver saver = new ij.io.FileSaver(imp2);
      saver.saveAsPng(file.getAbsolutePath());
   } catch (Exception e) {
      ij.IJ.error("Save Error", "Error saving file:" + e.getMessage());
      e.printStackTrace();
   } finally {
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
   }
}//GEN-LAST:event_captureImageMenuItemActionPerformed

/**
 * Gets a screen capture for the current image.
 *
 * @return the AWT Image.
 */
public Image getScreenCaptureCurrentImage() {
      MimsPlus imp = (MimsPlus) ij.WindowManager.getCurrentImage();
      final ImageWindow win = imp.getWindow();
      if (win == null) {
         return null;
      }
      //win.setVisible(false);
      //win.setVisible(true);
      //win.repaint();
      //win.toFront();
      try {
         Thread.sleep(500);
      } catch (Exception e) {
      }
      //win.toFront();
      Point loc = win.getLocation();
      ImageCanvas ic = win.getCanvas();
      ic.update(ic.getGraphics());
      
      Rectangle bounds = ic.getBounds();
      loc.x += bounds.x;
      loc.y += bounds.y;
      System.out.println("Printing at "+ loc.x +", "+ loc.y);
      Rectangle r = new Rectangle(loc.x, loc.y, bounds.width, bounds.height);
      Robot robot = null;
      try {
          robot = new Robot();
      } catch (AWTException ex) {
         IJ.error("Unable to capture image");
         return null;
      }
      robot.delay(100);
      Image img = robot.createScreenCapture(r);

      return img;
}


/**
 * Action method for the Utilities>Import .im  List menu item. Loads
 * a list of images caontained in a text file and concatenates them.
 */
private void importIMListMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_importIMListMenuItemActionPerformed
    com.nrims.data.LoadImageList testLoad = new com.nrims.data.LoadImageList(this);
    boolean read;
    read = testLoad.openList();
    if (!read) {
        return;
   }

    testLoad.printList();
    testLoad.simpleIMImport();
}//GEN-LAST:event_importIMListMenuItemActionPerformed
                                                                              
/**
 * Action method for the Utilities>Generate Stack menu item. Turns
 * a ratio or HSI image into a stack.
 */
private void genStackMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_genStackMenuItemActionPerformed

    MimsPlus img;
    //grab current window and try to cast
    try {
        img = (MimsPlus) ij.WindowManager.getCurrentImage();
    } catch (ClassCastException e) {
        //if it's some random image and we can't cast just return
        return;
    }

    ImageDataUtilities.generateStack(img, this);

}//GEN-LAST:event_genStackMenuItemActionPerformed
                                                                                         
/** Action method Utilities>Test menu item. Reserved for testing code. */
/**
 * Action method for File>Save Image and File>Save Session menu items.
 * Brings up a JFileChooser for saving the current image (or session).
 */
private void saveMIMSjMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveMIMSjMenuItemActionPerformed
   saveMIMS(evt);
   //TODO should add check if return false show error message
}//GEN-LAST:event_saveMIMSjMenuItemActionPerformed

/**
 * Called by the saveMIMSjMenuItemActionPerformed method and can be used to 
 * programatically save the current image. Brings up a file chooser.
 */
    private boolean saveMIMS(java.awt.event.ActionEvent evt) {
        String fileName;
        try {
            // User sets file prefix name
            boolean saveImageOnly = true;
            boolean sucess = false;
            //Save only image, enter save from gui because evt != null
            if (evt != null && evt.getActionCommand().equals(SAVE_IMAGE)) {
                saveImageOnly = true;
                System.out.println("Save image only.");

                //Save session, enter save from gui because evt != null
            } else if (evt != null && evt.getActionCommand().equals(SAVE_SESSION)) {
                saveImageOnly = false;
                System.out.println("Save session.");

                //saveMIMS(null) called
                //only called from checkCurrentFileStatusBeforeOpening()
            } else if (evt == null) {
                saveImageOnly = true;
                System.out.println("Force save image only.");
            }

            //Show Filechooser to get possible new name.
            MimsJFileChooser fc = new MimsJFileChooser(this);
            if (this.getImageFilePrefix() != null) {
                fc.setSelectedFile(new java.io.File(this.getImageFilePrefix() + NRRD_EXTENSION));
            }

            int returnVal = fc.showSaveDialog(jTabbedPane1);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                fileName = fc.getSelectedFile().getAbsolutePath();
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                sucess = saveSession(fileName, saveImageOnly);
            }
            return sucess;
        } catch (Exception e) {
            if (!silentMode) {
                ij.IJ.error("Save Error", "Error saving file:" + e.getMessage());
            } else {
                e.printStackTrace();
            }
            return false;
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

/**
 * Action method for the Utilities>Close>Close All Ratio Images.
 * Closes all ratio images.
 */
private void closeAllRatioMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeAllRatioMenuItemActionPerformed

    for (int i = 0; i < ratioImages.length; i++) {
        if (ratioImages[i] != null) {
            ratioImages[i].close();
        }
    }
}//GEN-LAST:event_closeAllRatioMenuItemActionPerformed

/**
 * Action method for the Utilities>Close>Close All HSI Images.
 * Closes all HSI images.
 */
private void closeAllHSIMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeAllHSIMenuItemActionPerformed

    for (int i = 0; i < hsiImages.length; i++) {
        if (hsiImages[i] != null) {
            hsiImages[i].close();
        }
    }
}//GEN-LAST:event_closeAllHSIMenuItemActionPerformed

/**
 * Action method for the Utilities>Close>Close All Sum Images.
 * Closes all sum images.
 */
private void closeAllSumMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_closeAllSumMenuItemActionPerformed

    for (int i = 0; i < sumImages.length; i++) {
        if (sumImages[i] != null) {
            sumImages[i].close();
        }
    }
}//GEN-LAST:event_closeAllSumMenuItemActionPerformed

/**
 * Exports all open sum images, HSI images, ratio images and composite images as .png files.
 */
public void exportPNGs(){
   File file = image.getImageFile();
    System.out.println(file.getParent()+File.separator);
    String dir = file.getParent()+File.separator;

    MimsPlus[] sum = getOpenSumImages();
    for( int i = 0; i < sum.length; i ++) {
        ImagePlus img = (ImagePlus)sum[i];
        ij.io.FileSaver saver = new ij.io.FileSaver(img);
        String name = getExportName(sum[i]) + ".png";
        File saveName = new File(dir+name);
        if (saveName.exists()) {
           for (int j = 1; j < 1000; j++){
              name = getExportName(sum[i]) + "_" + j + ".png";
              saveName = new File(dir+name);
              if (!saveName.exists())
                 break;
           }
        }
        saver.saveAsPng(dir+name);
    }

    MimsPlus[] hsi = getOpenHSIImages();
    for( int i = 0; i < hsi.length; i ++) {
        ImagePlus img = (ImagePlus)hsi[i];
        ij.io.FileSaver saver = new ij.io.FileSaver(img);
        String name = getExportName(hsi[i]) + ".png";
        File saveName = new File(dir+name);
        if (saveName.exists()) {
           for (int j = 1; j < 1000; j++){
              name = getExportName(hsi[i]) + "_" + j + ".png";
              saveName = new File(dir+name);
              if (!saveName.exists())
                 break;
           }
        }
        saver.saveAsPng(dir+name);
    }

    MimsPlus[] ratios = getOpenRatioImages();
    for( int i = 0; i < ratios.length; i ++) {
        ImagePlus img = (ImagePlus)ratios[i];
        ij.io.FileSaver saver = new ij.io.FileSaver(img);
        String name = getExportName(ratios[i]) + ".png";
        File saveName = new File(dir+name);
        if (saveName.exists()) {
           for (int j = 1; j < 1000; j++){
              name = getExportName(ratios[i]) + "_" + j + ".png";
              saveName = new File(dir+name);
              if (!saveName.exists())
                 break;
           }
        }
        saver.saveAsPng(dir+name);
    }


    MimsPlus[] comp = getOpenCompositeImages();
    for( int i = 0; i < comp.length; i ++) {
        ImagePlus img = (ImagePlus)comp[i];
        ij.io.FileSaver saver = new ij.io.FileSaver(img);
        String name = getExportName(comp[i]) + ".png";
        File saveName = new File(dir+name);
        if (saveName.exists()) {
           for (int j = 1; j < 1000; j++){
              name = getExportName(comp[i]) + "_" + j + ".png";
              saveName = new File(dir+name);
              if (!saveName.exists())
                 break;
           }
        }
        saver.saveAsPng(dir+name);
    }

}

/** Action method for Utilities>Export>Export All Derived. Exports all derived images and .png's. */
private void exportPNGjMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportPNGjMenuItemActionPerformed
   exportPNGs();
}//GEN-LAST:event_exportPNGjMenuItemActionPerformed

/** Action method for Utilities>Image Notes. Opens a text area for adding notes. */
private void imageNotesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_imageNotesMenuItemActionPerformed
    this.imgNotes.setVisible(true);
}//GEN-LAST:event_imageNotesMenuItemActionPerformed

/** Action method for Utilities>Composite menu item. Shows the compisite manager interface. */
private void compositeMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_compositeMenuItemActionPerformed
    cbControl.showCompositeManager();
}//GEN-LAST:event_compositeMenuItemActionPerformed

/** Action method for Utilities>Debug menu item. */
/** Not used. Unable to delete. Stupid netbeans. */
private void exportHSI_RGBAActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportHSI_RGBAActionPerformed
    // Empty
}//GEN-LAST:event_exportHSI_RGBAActionPerformed

/** Action method for View>Roi Manager menu item. Display the Roi Manager. */
private void roiManagerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_roiManagerMenuItemActionPerformed
   getRoiManager().viewManager();
}//GEN-LAST:event_roiManagerMenuItemActionPerformed

private void generateReportMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_generateReportMenuItemActionPerformed
    openReportGenerator();
}//GEN-LAST:event_generateReportMenuItemActionPerformed

/*
 * Method for opening report generator. If user closed or cancelled old report dialog,
 * this will report the old one. 
 */
public void openReportGenerator() {
    if(rg != null) {
        rg.setVisible(true);
        return;
    } else {
        rg = new ReportGenerator(this);
        rg.setVisible(true);
    }
}

private void batch2nrrdMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_batch2nrrdMenuItemActionPerformed
   convertManager cm = new convertManager(this);
   cm.setVisible(true);
   cm.selectFiles();
}//GEN-LAST:event_batch2nrrdMenuItemActionPerformed

private void stopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopButtonActionPerformed
   task.cancel(true);
}//GEN-LAST:event_stopButtonActionPerformed

private void DTCorrectionMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_DTCorrectionMenuItemActionPerformed
   if (image == null)
      return;

   int start_slice = getOpenMassImages()[0].getCurrentSlice();

   if (image.isDTCorrected())
      return;  
   else {
      setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
      // User sets file prefix name
      MimsJFileChooser fc = new MimsJFileChooser(this);
      fc.setDialogTitle("Save Corrected File As...");
      if (this.getImageFilePrefix() != null) {
         fc.setSelectedFile(new java.io.File(this.getImageFilePrefix() + NRRD_EXTENSION));
      }
      int returnVal = fc.showSaveDialog(jTabbedPane1);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
         String fileName = fc.getSelectedFile().getAbsolutePath();
         jProgressBar1.setString("Applying dead time correction...");
         applyDeadTimeCorrection(fileName);
         DTCorrectionMenuItem.setEnabled(false);
         jProgressBar1.setString("Dead time correction complete.");
         getOpenMassImages()[0].setSlice(start_slice);
         updateAllImages();
         ij.IJ.showMessage("Done");
      } else {
         DTCorrectionMenuItem.setSelected(false);
      }
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

   }
}//GEN-LAST:event_DTCorrectionMenuItemActionPerformed

private void QSACorrectionMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_QSACorrectionMenuItemActionPerformed
   if (qsam == null) {
      qsam = new QSAcorrectionManager(this);
   }
   qsam.setVisible(true);
}//GEN-LAST:event_QSACorrectionMenuItemActionPerformed

private void emptyTestMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_emptyTestMenuItemActionPerformed
    //empty?
    //String foo = "{{\\b DATE: }2012/11/08 12:45\\par}";
    //System.out.println("foo = "+foo);
    
    com.nrims.experimental.warp warp = new com.nrims.experimental.warp();
    warp.cellWarp(this);
}//GEN-LAST:event_emptyTestMenuItemActionPerformed

private void exportQVisMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exportQVisMenuItemActionPerformed

    MimsPlus img;
    try {
        img = (MimsPlus) ij.WindowManager.getCurrentImage();
    } catch (Exception e) {
        IJ.error("Error: current image not a MIMS image.");
        return;
    }

    if (img.getMimsType() != MimsPlus.HSI_IMAGE) {
        IJ.error("Error: current image not an HSI image.");
        return;
    }

    String empty = "";
    ij.gui.GenericDialog gd = new ij.gui.GenericDialog("Alpha min/max in counts");
    gd.addStringField("Alpha min:", empty, 20);
    gd.addStringField("Alpha max:", empty, 20);
    gd.showDialog();
    if (gd.wasCanceled()) {
        return;
    }
    String minstr = gd.getNextString();
    String maxstr = gd.getNextString();


    int minA = 0;
    int maxA = 0;

    //Do paramter/error checking
    try {
        minA = Integer.parseInt(minstr);
        maxA = Integer.parseInt(maxstr);
    } catch (Exception e) {
        IJ.error("Error: alpha min/max not integers.");
        return;
    }

    //Query user for filename
    MimsJFileChooser fc = new MimsJFileChooser(this);
    fc.setSelectedFile(new File(this.getLastFolder(), this.getImageFilePrefix() + ".dat"));
    MIMSFileFilter filter = new MIMSFileFilter("dat");
    filter.setDescription("QVis file");
    fc.addChoosableFileFilter(filter);
    fc.setFileFilter(filter);
    
    int returnVal = fc.showSaveDialog(this);
    if (returnVal == MimsJFileChooser.CANCEL_OPTION) {
        return;
    }
    
    File outFile = fc.getSelectedFile();
    
    String fileName = fc.getSelectedFile().getName();
    if (!fileName.endsWith(".dat")) {
        fileName = fileName + ".dat";
    }
    outFile = new java.io.File(outFile.getParentFile(), fileName);

    int startplane = this.getOpenMassImages()[0].getSlice();
    boolean success = com.nrims.data.exportQVis.exportHSI_RGBA(this, img, minA, maxA, outFile);
    if(!success) {
        IJ.error("Error: writing file failed.");
    }
    this.getOpenMassImages()[0].setSlice(startplane);
}//GEN-LAST:event_exportQVisMenuItemActionPerformed

    private void openNextMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openNextMenuItemActionPerformed
        openNext();
    }//GEN-LAST:event_openNextMenuItemActionPerformed

    private void RecomputeAllMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_RecomputeAllMenuItemActionPerformed
        recomputeAllImages();
    }//GEN-LAST:event_RecomputeAllMenuItemActionPerformed

    private void openNewWriterActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openNewWriterActionPerformed
        UnoPlugin.newDoc();
    }//GEN-LAST:event_openNewWriterActionPerformed

    private void insertPicFrameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_insertPicFrameActionPerformed
        UnoPlugin.insertEmptyOLEObject();
    }//GEN-LAST:event_insertPicFrameActionPerformed

    private void openNewDrawActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openNewDrawActionPerformed
        UnoPlugin.newDraw();
    }//GEN-LAST:event_openNewDrawActionPerformed

    private void openNewImpressActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openNewImpressActionPerformed
        UnoPlugin.newImpress();
    }//GEN-LAST:event_openNewImpressActionPerformed

   /**
    * Applies a correction to the current image and writes the file
    * to fileName.
    *
    * @param fileName - the corrected file.
    * @return <code>true</code> if applied correctly, otherwise <code>false</false>.
    */
   public boolean applyDeadTimeCorrection(String fileName) {

        com.nrims.data.massCorrection masscor = new com.nrims.data.massCorrection(this);
      
      float dwell = 0f;
      try {
         //dwell time in sec. (stored as ms in file)
         dwell = Float.parseFloat(this.getOpener().getDwellTime()) / 1000;
         masscor.performDeadTimeCorr(this.getOpenMassImages(), dwell);
         isDTCorrected = true;
         //log what was done
         this.getmimsLog().Log("DT correction dwelltime (s) = " + dwell);
      } catch (NumberFormatException e) {
         if (!silentMode)
            ij.IJ.error("Error", "Cannot get dwelltime from file header.");
         else
            e.printStackTrace();
         return false;
      } catch (Exception e) {
         if (!silentMode)
            ij.IJ.error("Error", "Error applying correction and/or saving file:" + e.getMessage());
         else
            e.printStackTrace();
         return false;
      }

      boolean saved = saveSession(fileName, true);
      if (!saved)
         return false;

      return true;
   }
    
/**
 * Updates the line profile to reflect the data stored in <code>newdata</code>.
 *
 * @param newdata the new data.
 * @param name the name to be on the legend.
 * @param width width of the line roi.
 */
public void updateLineProfile(double[] newdata, String name, int width) {
    if(this.lineProfile==null) {
        return;
    } else {
        lineProfile.updateData(newdata, name, width);
    }
}

   /**
    * Returns an instance of the RoiManager.
    *
    * @return an instance of the RoiManager.
    */
    public MimsRoiManager getRoiManager() {
        //roiManager = getRoiManager().getInstance();
        if (roiManager == null) {
            roiManager = new MimsRoiManager(this);
        }
        return roiManager;
    }

   /**
    * Returns an instance of the ReportGenerator
    *
    * @return an instance of the ReportGenerator
    */
    public ReportGenerator getReportGenerator() {
        return rg;
    }
    
    /**
    * Sets the report generator
    *
    */
    protected void setReportGenerator(ReportGenerator gen) {
        if(gen != null ) {
            rg = gen;
        } else{
            rg = null;
        }
    }

    /**
     * Returns the directory of the image currently opened.
     *
     * @return the directory of the current image file.
     */
    public String getImageDir() {
        String path = image.getImageFile().getParent();
        return path;
    }
    
    /**
     * Returns all the mass images, regardless if they are open or not.
     *
     * @return MimsPlus array of mass images.
     */
    public MimsPlus[] getMassImages() {
        return massImages;
    }

    /**
     * Returns the mass image with index <code>i</code>.
     *
     * @param i the index.
     * @return the mass image.
     */
    public MimsPlus getMassImage(int i) {
        if (i >= 0 && i < maxMasses) {
            return massImages[i];
        }
        return null;
    }

    /**
     * Return the index of the mass that falls closest to massValue (and within tolerance).
     *
     * @param massValue the massValue.
     * @param tolerance the range of possible masses from <code>massValue</code>.
     * @return the index
     */
    public int getClosestMassIndices(double massValue, double tolerance) {
       double massVal1, diff;
       double mindiff = Double.MAX_VALUE;
       int returnIdx = -1;

       if (tolerance > 0.0) {
          // do nothing
       }  else {
          return returnIdx;
       }

       String[] massNames = getOpener().getMassNames();
       for (int i = 0; i < massNames.length; i++){
          massVal1 = (new Double(getOpener().getMassNames()[i])).doubleValue();
          diff = Math.abs(massValue - massVal1);
          if (diff < mindiff && diff < tolerance)
             returnIdx = i;
       }

       return returnIdx;
    }

    /**
     * Returns all mass indices with a mass value within of <code>massValue</code> +/- <code>tolerance</code>.
     *
     * @param massValue the massValue.
     * @param tolerance the range of possible masses from <code>massValue</code>.
     * @return the indices
     */
    public int[] getMassIndices(double massValue, double tolerance) {
       double massVal1, diff;

       if (tolerance > 0.0) {
          // do nothing
       }  else {
          return null;
       }

       MimsPlus[] mps = getOpenMassImages();
       ArrayList imageList = new ArrayList<MimsPlus>();
       for (int i = 0; i < mps.length; i++){
          massVal1 = mps[i].getMassValue();
          diff = Math.abs(massValue - massVal1);
          if (diff < tolerance)
             imageList.add(mps[i]);
       }

       int[] indices = new int[imageList.size()];
       for (int i = 0; i < imageList.size(); i++)
          indices[i] = ((MimsPlus)imageList.get(i)).getMassIndex();

       return indices;
    }

    /**
     * Gets the mass value for mass image with index <code>i</code>.
     *
     * @param i the index.
     * @return the mass value.
     */
    public double getMassValue(int i) {
       double mass = -1.0;
       try {
          mass = new Double(getOpener().getMassNames()[i]);
       } catch (Exception e) {}
       return mass;
    }
    /**
     * Get the description of the file in a single String. 
     * @return string of the description
     */
    public String getDescription(){
        return mimsData.getText();
    }
    /**
     * Gets the ratio image with index <code>i</code>.
     *
     * @param i the index
     * @return the image.
     */
    public MimsPlus getRatioImage(int i) {
        if (i >= 0 && i < maxMasses) {
            return ratioImages[i];
        }
        return null;
    }
    

    /**
     * Gets the HSI image with index <code>i</code>.
     *
     * @param i the index
     * @return the image.
     */
    public MimsPlus getHSIImage(int i) {
        if (i >= 0 && i < maxMasses) {
            return hsiImages[i];
        }
        return null;
    }

    /**
     * Returns all the open Mass, Sum, Ratio, and Hsi
     *
     * @return array of images.
     */
    public MimsPlus[] getOpenImages() {
        MimsPlus[] massImages = getOpenMassImages();
        MimsPlus[] sumImages = getOpenSumImages();
        MimsPlus[] ratioImages = getOpenRatioImages();
        MimsPlus[] hsiImages = getOpenHSIImages();

        MimsPlus[] allImages = new MimsPlus[massImages.length + sumImages.length + ratioImages.length + hsiImages.length];
        int i = 0;
        for (MimsPlus mp : massImages) {
           allImages[i] = mp;
           i++;
        }
        for (MimsPlus mp : sumImages) {
           allImages[i] = mp;
           i++;
        }
        for (MimsPlus mp : ratioImages) {
           allImages[i] = mp;
           i++;
        }
        for (MimsPlus mp : hsiImages) {
           allImages[i] = mp;
           i++;
        }

        return allImages;
    }

    /**
     * Returns the open mass images as an array.
     *
     * @return array of images.
     */
    public MimsPlus[] getOpenMassImages() {
        int i, nOpen = 0;
        for (i = 0; i < massImages.length; i++) {
            if (massImages[i] != null && bOpenMass[i]) {
                nOpen++;
            }
        }
        MimsPlus[] mp = new MimsPlus[nOpen];
        if (nOpen == 0) {
            return mp;
        }
        for (i = 0        , nOpen = 0; i < massImages.length; i++) {
            if (massImages[i] != null && bOpenMass[i]) {
                mp[nOpen++] = massImages[i];
            }
        }
        return mp;
    }

    /**
     * Returns the open ratio images as an array.
     *
     * @return array of images.
     */
    public MimsPlus[] getOpenRatioImages() {
        int i, nOpen = 0;
        for (i = 0; i < maxMasses; i++) {
            if (ratioImages[i] != null) {
                nOpen++;
            }
        }
        MimsPlus[] mp = new MimsPlus[nOpen];
        if (nOpen == 0) {
            return mp;
        }
        for (i = 0 , nOpen = 0; i < maxMasses; i++) {
            if (ratioImages[i] != null) {
                mp[nOpen++] = ratioImages[i];
            }
        }
        return mp;
    }

    /**
     * Returns the open composite images as an array.
     *
     * @return array of images.
     */
    public MimsPlus[] getOpenCompositeImages() {
        int i, nOpen = 0;
        for (i = 0; i < maxMasses; i++) {
            if (compImages[i] != null) {
                nOpen++;
            }
        }
        MimsPlus[] mp = new MimsPlus[nOpen];
        if (nOpen == 0) {
            return mp;
        }
        for (i = 0        , nOpen = 0; i < maxMasses; i++) {
            if (compImages[i] != null) {
                mp[nOpen++] = compImages[i];
            }
        }
        return mp;
    }

    /**
     * Add the key value pair to the metaData hashmap.
     * Nulls not allowed as key or value.
     *
     * @param key the key
     * @param value the value
     * @return <code>true</code> if succesfull
     */
    public synchronized void insertMetaData(String key, String value) {
       metaData.put(key, value);
       return;
    }

    /**
     * Return the value associated with key.
     *
     * @param key the key
     * @return the value
     */
    public synchronized String getMetaDataFromKey(String key) {
       return (String)metaData.get(key);
    }

    /**
     * Return the map.
     *
     * @return the map
     */
    public HashMap getMetaData() {
       return metaData;
    }

    /**
     * Returns the open HSI images as an array.
     *
     * @return array of images.
     */
    public MimsPlus[] getOpenHSIImages() {
        int i, nOpen = 0;
        for (i = 0; i < maxMasses; i++) {
            if (hsiImages[i] != null) {
                nOpen++;
            }
        }
        MimsPlus[] mp = new MimsPlus[nOpen];
        if (nOpen == 0) {
            return mp;
        }
        for (i = 0        , nOpen = 0; i < maxMasses; i++) {
            if (hsiImages[i] != null) {
                mp[nOpen++] = hsiImages[i];
            }
        }
        return mp;
    }

    /**
     * Returns the open segmented images as an array.
     *
     * @return array of images.
     */
    public MimsPlus[] getOpenSegImages() {
        int i, nOpen = 0;
        for (i = 0; i < maxMasses; i++) {
            if (segImages[i] != null) {
                nOpen++;
            }
        }
        MimsPlus[] mp = new MimsPlus[nOpen];
        if (nOpen == 0) {
            return mp;
        }
        for (i = 0, nOpen = 0; i < maxMasses; i++) {
            if (segImages[i] != null) {
                mp[nOpen++] = segImages[i];
            }
        }
        return mp;
    }

    /**
     * Returns the open sum images as an array.
     *
     * @return array of images.
     */
    public MimsPlus[] getOpenSumImages() {
        int i, nOpen = 0;
        for (i = 0; i < 2*maxMasses; i++) {
            if (sumImages[i] != null) {
                nOpen++;
            }
        }
        MimsPlus[] mp = new MimsPlus[nOpen];
        if (nOpen == 0) {
            return mp;
        }
        for (i = 0, nOpen = 0; i < 2*maxMasses; i++) {
            if (sumImages[i] != null) {
                mp[nOpen++] = sumImages[i];
            }
        }
        return mp;
    }

    /**
     * Returns the <code>HSIProps</code> object for all open HSI images.
     *
     * @return array of <code>HSIProps</code> objects.
     */
    public HSIProps[] getOpenHSIProps() {
       MimsPlus[] hsi = getOpenHSIImages();
       HSIProps[] hsi_props = new HSIProps[hsi.length];
       for (int i=0; i<hsi.length; i++){
          hsi_props[i] = hsi[i].getHSIProps();         
       }
       return hsi_props;
    }

    /**
     * Returns the <code>RatioProps</code> object for all open ratio images.
     *
     * @return array of <code>RatioProps</code> objects.
     */
    public RatioProps[] getOpenRatioProps() {
       MimsPlus[] rto = getOpenRatioImages();
       RatioProps[] rto_props = new RatioProps[rto.length];
       for (int i=0; i<rto.length; i++){
          rto_props[i] = rto[i].getRatioProps();
       }
       return rto_props;
    }

    /**
     * Returns the <code>SumProps</code> object for all open sum images.
     *
     * @return array of <code>SumProps</code> objects.
     */
    public SumProps[] getOpenSumProps(){
       MimsPlus[] sum = getOpenSumImages();
       SumProps[] sum_props = new SumProps[sum.length];
       for (int i=0; i<sum.length; i++){
          sum_props[i] = sum[i].getSumProps();
          sum_props[i].setXWindowLocation(sum[i].getWindow().getX());
          sum_props[i].setYWindowLocation(sum[i].getWindow().getY());
          if (sum_props[i].getSumType() == SumProps.RATIO_IMAGE) {
             sum_props[i].setNumMassValue(getMassValue(sum_props[i].getNumMassIdx()));
             sum_props[i].setDenMassValue(getMassValue(sum_props[i].getDenMassIdx()));
          } else if (sum_props[i].getSumType() == SumProps.MASS_IMAGE) {
             sum_props[i].setParentMassValue(getMassValue(sum_props[i].getParentMassIdx()));
          }

          //should these be set inside getprops?
          //maybe...
          sum_props[i].setMinLUT(sum[i].getDisplayRangeMin());
          sum_props[i].setMaxLUT(sum[i].getDisplayRangeMax());

          sum_props[i].setMag(sum[i].getCanvas().getMagnification());
       }
       return sum_props;
    }

    /**
     * Returns the mass, ratio, HSI or sum image with the name <code>name</code>.
     *
     * @param name the name
     * @return an image.
     */
    public MimsPlus getImageByName(String name) {
        MimsPlus mp = null;
        MimsPlus[] tempimages;

        // Mass images.
        tempimages = getOpenMassImages();        
        for(int i=0; i<tempimages.length; i++){
            if(name.equals(tempimages[i].getTitle())) {
                return tempimages[i];
            }
        }

        // Ratio images.
        tempimages = getOpenRatioImages();        
        for(int i=0; i<tempimages.length; i++){
            if(name.equals(tempimages[i].getTitle())) {
                return tempimages[i];
            }
        }

        // Hsi images.
        tempimages = getOpenHSIImages();
        for(int i=0; i<tempimages.length; i++){
            if(name.equals(tempimages[i].getTitle())) {
                return tempimages[i];
            }
        }

        // Sum images.
        tempimages = getOpenSumImages();
        for(int i=0; i<tempimages.length; i++){
            if(name.equals(tempimages[i].getTitle())) {
                return tempimages[i];
            }
        }

        return mp;
    }

    /**
     * Sets if all mass images should croll through
     * the stack in unison (synched).
     *
     * @param bSync Set to <code>true</code> if mass
     * images should scroll in sych, otherwise <code>false</code>.
     */
    public void setSyncStack(boolean bSync) {
        bSyncStack = bSync;
    }

    /**
     * Returns a pointer to the <code>MimsData</code> object
     * which controls the "Data" tab.
     *
     * @return the <code>MimsData</code> object.
     */
    public MimsData getMimsData() {
        return mimsData;
    }

    /**
     * Returns a pointer to the <code>MimsHSIView</code> object
     * which controls the "Process" tab.
     * 
     * @return the <code>MimsHSIView</code> object.
     */
    public MimsHSIView getHSIView() {
        return hsiControl;
    }

    /**
     * Returns a pointer to the <code>MimsLog</code> object
     * which controls the "MIMS Log" tab.
     *
     * @return the <code>MimsLog</code> object.
     */
    public MimsLog getmimsLog() {
        return mimsLog;
    }

    /**
     * Returns a pointer to the <code>MimsCBControl</code> object
     * which controls the "Contrast" tab.
     *
     * @return the <code>MimsCBControl</code> object.
     */
    public MimsCBControl getCBControl(){
       return cbControl;
    }

    /**
     * Returns a pointer to the <code>MimsStackEditor</code> object
     * which controls the "Stack Editing" tab.
     *
     * @return the <code>MimsStackEditor</code> object.
     */
    public MimsStackEditor getmimsStackEditing() {
        return mimsStackEditing;
    }

    /**
     * Returns a pointer to the <code>MimsTomography</code> object
     * which controls the "Tomography" tab.
     *
     * @return the <code>MimsTomography</code> object.
     */
    public MimsTomography getmimsTomography() {
        return mimsTomography;
    }

     /**
     * Returns a pointer to the <code>SegmentationForm</code> object
     * which controls the "Segmentation" tab.
     *
     * @return the <code>MimsTomography</code> object.
     */
    public SegmentationForm getmimsSegmentation() {
        return segmentation;
    }
    
    public static UI getInstance() {
        return (UI)ui;
    }
    
    
    /**
     * Returns a pointer to the <code>MimsAction</code> object.
     * The <code>MimsAction</code> object stores all the modifications
     * made to the current image (translations, plane deletions, etc).
     *
     * @return the <code>MimsAction</code> object.
     */
    public MimsAction getmimsAction() {
        return mimsAction;
    }

    /**
     * Sets the flag indicating the plugin is in the process of updating.
     *
     * @param bool <code>true</code> if updating, otherwise false.
     */
    public void setUpdating(boolean bool) {
        bUpdating = bool;
    }

    /**
     * Return <code>true</code> if the plugin is in the process of opening
     * an image.
     *
     * @return <code>true</code> if the plugin is in the process of opening
     * an image, otherwise <code>false</code>.
     */
    public boolean isOpening() {
        return currentlyOpeningImages;
    }

    /**
     * Return <code>true</code> if the plugin is in silent mode.
     *
     * @return <code>true</code> if the plugin is in silent mode.
     */
    public boolean isSilentMode() {
        return silentMode;
    }

    /**
     * Gets the flag indicating if the plugin is in the process of updating.
     *
     * @return <code>true</code> if updating, otherwise false.
     */
    public boolean isUpdating() {
        return bUpdating;
    }

    /**
     * Returns a link the <code>PrefFrame</code> object for referencing user preferences.
     *
     * @return the <code>PrefFrame</code> object.
     */
    public PrefFrame getPreferences() {
        return prefs;
    }

    /**
     * Returns a link the <code>Opener</code> object for getting image data and metadata.
     * The UI class stores a list of opener objects in the case that the image was
     * concatenated or derived from multiple files. This method returns the member variable
     * <code>image</code> which will correspond to the FIRST image opened.
     *
     * @return the <code>Opener</code> object.
     */
    public Opener getOpener() {
        return image;
    }   

    /**
     * Gets <code>Opener</code> with name <code>name</code> from the list of opener objects.
     *
     * @param name the name of the opener object.
     * @return the <code>Opener</code>.
     */
    public Opener getFromOpenerList(String name){
        return (Opener)openers.get(name);
    }

    /**
     * Adds the <code>Opener</code> object with name <code>name</code>
     * to the list of openers.
     *
     * @param fileName name of file.
     * @param opener opener object.
     */
    public void addToOpenerList(String fileName, Opener opener) {
       openers.put(fileName, opener);
    }

   /**
    * Determines the bahavior when an image window is made active.
    *
    * @param mp the image.
    */
   public void setActiveMimsPlus(MimsPlus mp) {
      if (mp == null)
         return;
      
      if (mp.getMimsType() == MimsPlus.HSI_IMAGE) {
         hsiControl.setCurrentImage(mp);
         hsiControl.setProps(mp.getHSIProcessor().getHSIProps());
      } else if (mp.getMimsType() == MimsPlus.RATIO_IMAGE) {
         hsiControl.setCurrentImage(mp);
         hsiControl.setProps(mp.getRatioProps());
      }
   }

    /**
     * Displays the String <code>msg</code> in the status bar.
     *
     * @param msg the message to display.
     */
    public void updateStatus(String msg) {
       jProgressBar1.setString(msg);
    }

    /**
     * Sets the radius of the radius of the median filter.
     *
     * @param r the radius.
     */
    public void setMedianFilterRadius(double r) {
        this.medianFilterRadius = r;        
    }

    /**
     * Gets the radius of the radius of the median filter.
     *
     * @return the radius.
     */
    public double getMedianFilterRadius() {
        return this.medianFilterRadius;
    }

    /**N
     * Returns the directory of the last location used by
     * the user for loading or saving image data.
     *
     * @return the last folder.
     */
    public String getLastFolder() {
        return lastFolder;
    }

    /**
     * Set the directory of the last location used
     * to retrieve data.
     *
     * @param the last folder.
     */
    public void setLastFolder(String path) {
       if (path == null)
          return;

       File folder = new File(path);
       if (folder.exists() && folder.isDirectory())
          lastFolder = path;
    }

    /**
     * Set the directory of the last location used
     * to retrieve data.
     *
     * @param the last folder.N
     */
    public void setLastFolder(File folder) {
       if (folder.exists() && folder.isDirectory())
          setLastFolder(folder.getAbsolutePath());
    }

    /**
     * Sets the ImageJ default directory so that when imageJ
     * file choosers are opened, they are pointing to the
     * directory <code>dir</code>.
     *
     * @param dir the directory.
     */
    public void setIJDefaultDir(String dir) {       
       File defaultDir = new File(dir);
       if (!defaultDir.exists())
          return;
       ij.io.OpenDialog temp = new ij.io.OpenDialog("", dir);
       temp.setDefaultDirectory(dir);
       temp = null;
    }

    /**
     * This is a copy of imageJ's WindowOrganizer.tileWindows() method.
     *
     * @param tileY amount of pixels to offset the vertical position by.
     */
    public void tileWindows() {
        final int XSTART = 4, GAP = 2;
        int tileY = prefs.getTileY();
        int YSTART = 80 + tileY;
        int titlebarHeight = IJ.isMacintosh() ? 40 : 20;

        Dimension screen = IJ.getScreenSize();
        int minWidth = Integer.MAX_VALUE;
        int minHeight = Integer.MAX_VALUE;
        boolean allSameSize = true;
        int width = 0, height = 0;
        double totalWidth = 0;
        double totalHeight = 0;
        MimsPlus[] windows = getOpenMassImages();
        double[] massVals = new double[windows.length];
        int[] win_ids = new int[windows.length];
        int n = 0;
        MimsPlus[] massImages = windows;
        ArrayList<MimsPlus> sortedMassImages = new ArrayList<MimsPlus>();
        MimsPlus[] sumImages = getOpenSumImages();
        MimsPlus[] ratioImages = getOpenRatioImages();
        MimsPlus[] hsiImages = getOpenHSIImages();
        n = 0;
        int row = 0;
        MimsPlus curZero = null;
        if (massImages[0].getMassValue() != 0) 
            sortedMassImages.add(massImages[0]);
        else
            curZero = massImages[0];
        for (int i = 1; i < massImages.length; i++){
            MimsPlus cur = massImages[i];
            MimsPlus prev = massImages[i-1];
            if (prev.getMassValue() == 0 && i > 1) prev = massImages[i-2];
            if (prev.getMassValue() > cur.getMassValue()){ 
                 if (cur.getMassValue() != 0){
                    if (curZero != null) sortedMassImages.add(curZero);
                    if (row == 0) row = sortedMassImages.size();
                 }else curZero = cur;
            }
            if (cur.getMassValue() != 0) sortedMassImages.add(cur);
            if (i == massImages.length-1 && curZero != null) sortedMassImages.add(curZero);
        }
        int[] wList = new int[sortedMassImages.size() + sumImages.length + ratioImages.length + hsiImages.length];
        int j = 0;
        //need to add all types of images into win_ids so they are tiled too
        for (int i = 0; i < sortedMassImages.size(); i++) 
            wList[j++] = sortedMassImages.get(i).getID();
        for (MimsPlus mp : sumImages) {
            wList[j++] = mp.getID();
        }
        for (MimsPlus mp : ratioImages) {
            wList[j++] = mp.getID();
        }
        for (MimsPlus mp : hsiImages) {
            wList[j++] = mp.getID();
        }
        for (int i=0; i<wList.length; i++) {
            //ImageWindow win = getWindow(wList[i]);
            ImageWindow win = null;
            ImagePlus imp = WindowManager.getImage(wList[i]);
            if (imp!=null)
              win = imp.getWindow();
            if (win==null)
                continue;
            Dimension d = win.getSize();
            int w = d.width;
            int h = d.height + titlebarHeight;
            if (i==0) {
                width = w;
                height = h;
            }
            if (w!=width || h!=height)
                allSameSize = false;
            if (w<minWidth)
                minWidth = w;
            if (h<minHeight)
                minHeight = h;
            totalWidth += w;
            totalHeight += h;
        }
        int nPics = wList.length;
        double averageWidth = totalWidth/nPics;
        double averageHeight = totalHeight/nPics;
        int tileWidth = (int)averageWidth;
        int tileHeight = (int)averageHeight;
        //IJ.write("tileWidth, tileHeight: "+tileWidth+" "+tileHeight);
        int hspace = screen.width - 2 * GAP;
        if (tileWidth>hspace)
            tileWidth = hspace;
        int vspace = screen.height - YSTART;
        if (tileHeight>vspace)
            tileHeight = vspace;
        int hloc, vloc;
        boolean theyFit;
        do {
            hloc = XSTART;
            vloc = YSTART;
            theyFit = true;
            int i = 0;
            do {
                i++;
                if (hloc+tileWidth>screen.width) {
                    hloc = XSTART;
                    vloc = vloc + tileHeight;
                    if (vloc+tileHeight> screen.height)
                        theyFit = false;
                }
                hloc = hloc + tileWidth + GAP;
            } while (theyFit && (i<nPics));
            if (!theyFit) {
                tileWidth = (int)(tileWidth*0.98 +0.5);
                tileHeight = (int)(tileHeight*0.98+0.5);
            }
        } while (!theyFit);
        int nColumns = (screen.width-XSTART)/(tileWidth+GAP);
        int nRows = nPics/nColumns;
        if ((nPics%nColumns)!=0)
            nRows++;
        hloc = XSTART;
        vloc = YSTART;
        int currentlyInRow = 0;
        for (int i=0; i<nPics; i++) {
            if (hloc+tileWidth>screen.width || (row != 0 && currentlyInRow >= row)) {
                hloc = XSTART;
                vloc = vloc + tileHeight;
                currentlyInRow = 0;
            }
            ImageWindow win = null;
            ImagePlus imp = WindowManager.getImage(wList[i]);
            if (imp!=null)
              win = imp.getWindow();
            if (win!=null) {
                win.setLocation(hloc, vloc);
                ImageCanvas canvas = win.getCanvas();
                while (win.getSize().width*0.85>=tileWidth && canvas.getMagnification()>0.03125)
                    canvas.zoomOut(0, 0);
                win.toFront();
            }
            currentlyInRow++;
            hloc += tileWidth + GAP;
        }
    }

    @Override
    public void run(String cmd) {
        OMLOGGER.info("UI.run");
        
        if(cmd!=null) {
            OMLOGGER.info("Fiji args: " + cmd);
            if (cmd.equals("-t"))
                initComponentsTesting();
        }
        
        super.run("");
        setVisible(true);
    }

    /**
     * @param args the command line argumentsS
     */
    //NOT hit if starting plugin from ImageJ plugins menu
    //Program startup flow
    //
    //If being called from Netbeans or the main class is being called in an executable (runUI) then we will hit this function first
    //then we create the nrimsPlugin class, the construction of which does nothing but log some info
    //we then call run and pass it the arguments we recieved in main
    //run will parse the arguments and configure the UI globals
    //then it will create a new instance of UI and set it visible
    //it then checks if there are any arguments passed from ImageJ
    //then passes those to UI.run(), which only checks for the testing flag.
    public static void main(String args[]) {
       NRIMS_Plugin nrimsPlugin = new NRIMS_Plugin();
       nrimsPlugin.run(FileUtilities.joinArray(args));
       //to emulate hitting it from the gui, comment out the above and uncomment the below:
       //nrimsPlugin.run("");
   }
    
    /**
     * Invoked when task's progress property changes.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress" == evt.getPropertyName()) {
            int progress = (Integer) evt.getNewValue();
            jProgressBar1.setValue(progress);
        }
    }

    /**
     * Checks if the current file has been modified. If so,
     * the user is prompted to see if he/she wants to save.
     *
     * @return <code>true</code> if a save action is required, otherwise false.
     */
   private boolean checkCurrentFileStatusBeforeOpening() {
       if (mimsAction != null && mimsAction.isImageModified()) {
          int n = JOptionPane.showConfirmDialog(
                  this,
                  "The current file has been modified.\n\t\tDo you want to save the changes?\n",
                  "Warning",
                  JOptionPane.YES_NO_CANCEL_OPTION,
                  JOptionPane.WARNING_MESSAGE);

          if (n == JOptionPane.NO_OPTION)
             return true;
          if (n == JOptionPane.YES_OPTION) {
             return saveMIMS(null);
          }
          if (n == JOptionPane.CANCEL_OPTION)
             return false;
       }
       return true;
   }
    public int getInterval(){
        if (prefs != null){
            return 1000*prefs.getAutoSaveInterval();
        }else{
            return 120000;
        }
    }
    /**
     * openNext(): called to open next file in same folder as current image.
     */
    public void openNext(){
        if (image != null){
            String imageName = image.getImageFile().getName();
            String path = getLastFolder();
            String nextPath = FileUtilities.getNext(path, imageName, true);
             File nextFile = new File(nextPath);
            if(nextFile != null){
                 System.out.println(nextFile.getName());
                if (checkCurrentFileStatusBeforeOpening()){
                    openFileInBackground(nextFile);
                }
            }else{
                JOptionPane.showMessageDialog(this, "Unable to find next file", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }else{
            JOptionPane.showMessageDialog(this, "No image loaded", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
   
    /**
     * The FileOpenTask will open image files either in the background or
     * inline. To open a file in the background, use the following code sequence:
     *
     *   FileOpenTask fileOpenTask = new FileOpenTask(file, ui);
     *   fileOpenTask.addPropertyChangeListener(this); // for progressbar updates.
     *   fileOpenTask.execute();
     *
     * To open a file inline, use the following code sequence:
     *
     *   FileOpenTask fileOpenTask = new FileOpenTask(file, ui);
     *   fileOpenTask.addPropertyChangeListener(this); // for progressbar updates.
     *   fileOpenTask.doInBackground();
     *
     * It is counter intuitive the the doInBackground() method does NOT launch the
     * task in the background but that is the result of the naming convention
     * used by java.
     */
    
    class FileOpenTask extends SwingWorker<Boolean, Void> {

       UI ui;
       File file;

       public FileOpenTask(File file, UI ui) {
          this.file = file;
          this.ui = ui;
          OMLOGGER.info("OpenMIMS: UI.FileOpenTask(): " + file.getAbsolutePath());
       }

        /*
         * Main task. Executed in background thread.
         */
        @Override
       public Boolean doInBackground() {

          // Get properties for derived images
          RatioProps[] rto_props = getOpenRatioProps();
          HSIProps[] hsi_props = getOpenHSIProps();
          SumProps[] sum_props = getOpenSumProps();

          // Get previous image size.
          int old_width = 0;
          int old_height = 0;
          if (image != null) {
             old_width = image.getWidth();
             old_height = image.getHeight();
          }

          // Roi Manager visible.
          boolean roiManagerVisible = getRoiManager().isVisible();

          // Open the file.
          boolean opened = openFile(file);
          if (!opened) {
             openProcessFailedOrCanceled();
             return false;
          }
          stopButton.setEnabled(false);

          boolean isRoiFile = (file.getAbsolutePath().endsWith(ROI_EXTENSION) || file.getAbsolutePath().endsWith(ROIS_EXTENSION));
          if (isRoiFile)
             return true;

          doneLoadingFile();
          
          // Get new image size.
          int new_width = image.getWidth();
          int new_height = image.getHeight();
          boolean same_size = ((old_height == new_height) && (old_width == new_width)); 

          // Perform some checks to see if we wanna restore state.          
          boolean isImageFile = (file.getAbsolutePath().endsWith(NRRD_EXTENSION) || file.getAbsolutePath().endsWith(MIMS_EXTENSION));
          if (isImageFile) restoreState(rto_props, hsi_props, sum_props, same_size, roiManagerVisible);

          // Set up roi manager.
          MimsRoiManager rm = getRoiManager();
          if (rm != null) rm.resetRoiLocationsLength();

          // Autocontrast mass images.
          autoContrastImages(getOpenMassImages());
          //TODO, remove/refactor
          //This overrides contrast of sum images set in restoreState()
          //autoContrastImages(getOpenSumImages());

          // Update notes gui
          if (image != null) imgNotes.setOutputFormatedText(image.getNotes());

          // Update hashmap
          if (image != null) metaData = image.getMetaDataKeyValuePairs();

          // Update dt correction flag
          if (image != null) isDTCorrected = image.isDTCorrected();
          DTCorrectionMenuItem.setSelected(isDTCorrected);
          DTCorrectionMenuItem.setEnabled(!isDTCorrected);

          // Update QSA correction flag
          if (image != null) isQSACorrected = image.isQSACorrected();
          QSACorrectionMenuItem.setSelected(isQSACorrected);
          QSACorrectionMenuItem.setEnabled(!isQSACorrected);


          return true;
       }

       /**
        * Relegates behavior for opening any of the various MimsPlus file types.
        * @param file to be opened.
        */
       public boolean openFile(File file) {

          boolean onlyShowDraggedFile = true;

          String fileName = file.getName();
          if (fileName.endsWith(NRRD_EXTENSION) || fileName.endsWith(MIMS_EXTENSION)
                  || fileName.endsWith(RATIO_EXTENSION) || fileName.endsWith(HSI_EXTENSION)
                  || fileName.endsWith(SUM_EXTENSION) || fileName.endsWith(ROIS_EXTENSION)
                  || fileName.endsWith(ROI_EXTENSION) || fileName.endsWith(NRRD_HEADER_EXTENSION)
                  || fileName.endsWith(RATIOS_EXTENSION) || fileName.endsWith(HSIS_EXTENSION)
                  || fileName.endsWith(SUMS_EXTENSION) || fileName.endsWith(SESSIONS_EXTENSION)) {
          } else {
             String fileType;
             int lastIndexOf = fileName.lastIndexOf(".");
             if (lastIndexOf >= 0) {
                fileType = fileName.substring(fileName.lastIndexOf("."));
             } else {
                fileType = fileName;
             }
             IJ.error("Unable to open files of type: " + fileType);
             return false;
          }

          lastFolder = file.getParent();
          setIJDefaultDir(lastFolder);
          Object obj;
          try {
             if (file.getAbsolutePath().endsWith(NRRD_EXTENSION)
                     || file.getAbsolutePath().endsWith(MIMS_EXTENSION) || file.getAbsolutePath().endsWith(NRRD_HEADER_EXTENSION)) {
                onlyShowDraggedFile = false;
                if(!loadMIMSFile(file))
                   return false;
             } else if (file.getAbsolutePath().endsWith(RATIO_EXTENSION)) {
                if ((obj = FileUtilities.readObjectFromXML(file))instanceof RatioProps) {
                   RatioProps ratioprops = (RatioProps) obj;
                   File dataFile = new File(file.getParent(), ratioprops.getDataFileName());
                   if(!loadMIMSFile(dataFile))
                      return false;
                   doneLoadingFile();
                   sessionOpened = true;
                   MimsPlus mp = new MimsPlus(ui, ratioprops);
                   mp.showWindow();
                }
             } else if (file.getAbsolutePath().endsWith(HSI_EXTENSION)) {
                if ((obj = FileUtilities.readObjectFromXML(file)) instanceof HSIProps) {
                   HSIProps hsiprops = (HSIProps) obj;
                   File dataFile = new File(file.getParent(), hsiprops.getDataFileName());
                   if(!loadMIMSFile(dataFile))
                      return false;
                   doneLoadingFile();
                   sessionOpened = true;
                   MimsPlus mp = new MimsPlus(ui, hsiprops);
                   mp.showWindow();
                }
             } else if (file.getAbsolutePath().endsWith(SUM_EXTENSION)) {
                if ((obj = FileUtilities.readObjectFromXML(file)) instanceof SumProps) {
                   SumProps sumprops = (SumProps) obj;
                   File dataFile = new File(file.getParent(), sumprops.getDataFileName());
                   if(!loadMIMSFile(dataFile))
                      return false;
                   doneLoadingFile();
                   sessionOpened = true;
                   MimsPlus sp = new MimsPlus(ui, sumprops, null);
                   sp.showWindow();
                }
             } else if (file.getAbsolutePath().endsWith(SESSIONS_EXTENSION)) {
                  onlyShowDraggedFile = false;
                  //get all xml objects contained within zip
                  ArrayList entries = FileUtilities.openXMLfromZip(file);
                  if (entries == null || entries.isEmpty()) {
                      JOptionPane.showMessageDialog(ui, ".session.zip is empty/corrupt", "File Read Error", JOptionPane.ERROR_MESSAGE);
                      return false;
                  }
                  //sort the objects into props
                  MimsPlus sp;
                 for (Object entry : entries) {
                     if (entry instanceof SumProps) {
                         SumProps sumprops = (SumProps) entry;
                         if (!sessionOpened) {
                             if (!loadMIMSFile(new File(file.getParent(), sumprops.getDataFileName())))
                                 return false;
                             doneLoadingFile();
                             sessionOpened = true;
                         }
                         sp = new MimsPlus(ui, sumprops, null);
                         sp.showWindow();
                     } else if (entry instanceof RatioProps) {
                         RatioProps ratioprops = (RatioProps) entry;
                         if (!sessionOpened) {
                             if (!loadMIMSFile(new File(file.getParent(), ratioprops.getDataFileName())))
                                 return false;
                             doneLoadingFile();
                             sessionOpened = true;
                         }
                         sp = new MimsPlus(ui, ratioprops);
                         sp.showWindow();
                     } else if (entry instanceof HSIProps) {
                         HSIProps hsiprops = (HSIProps) entry;
                         if (!sessionOpened) {
                             if (!loadMIMSFile(new File(file.getParent(), hsiprops.getDataFileName())))
                                 return false;
                             doneLoadingFile();
                             sessionOpened = true;
                         }
                         sp = new MimsPlus(ui, hsiprops);
                         sp.showWindow();
                     }
                 }
                 
             } else if (file.getAbsolutePath().endsWith(ROIS_EXTENSION)
                     || file.getAbsolutePath().endsWith(ROI_EXTENSION)) {
                onlyShowDraggedFile = false;
                getRoiManager().open(file.getAbsolutePath(), true);
                updateAllImages();
                getRoiManager().showFrame();
             }
          } catch (Exception e) {
             IJ.error("Failed to open " + file + ":" + e.getMessage() + "\n");
             e.printStackTrace();
             return false;
          }

          updateScrollbars();

          if (onlyShowDraggedFile) {
             MimsPlus[] mps = getOpenMassImages();
             for (int i = 0; i < mps.length; i++) {
                mps[i].hide();
             }
          }

          return true;
       }


       /**
        * Opens an image file in the .im or .nrrd file format.
        * @param file absolute file path.
        * @throws java.lang.NullPointerException
        */
       public synchronized boolean loadMIMSFile(File file) throws NullPointerException {
          if (!file.exists()) {
             throw new NullPointerException("File " + file.getAbsolutePath() + " does not exist!");
          }
          long startTime = System.nanoTime();
          boolean isIM = false;
          boolean isNRRD = false;
          int progress = 0;
          closeCurrentImage();
          getRoiManager().roijlist.clearSelection();

          try {

             // Set up the Opener object depending on file type.
             if (file.getName().endsWith(MIMS_EXTENSION)) {
                image = new Mims_Reader(file);
                isIM = true;
             } else if (file.getName().endsWith(NRRD_EXTENSION)) {
                image = new Nrrd_Reader(file);
                isNRRD = true;
             } else if (file.getName().endsWith(NRRD_HEADER_EXTENSION)) {
                image = new Nrrd_Reader(file);
                isNRRD = true;
             }else {
                return false;
             }

             // Make sure there is agreement between the file size and header.
             boolean checks_out = image.performFileSanityCheck();
             if (checks_out == false) {
                if (ui.silentMode == true) {
                   OMLOGGER.warning("File has a bad header.");
                   return false;
                } else {
                   OpenerManager opg = new OpenerManager(ui, image);
                   opg.setModal(true);
                   opg.setLocation(ui.getLocation().x + 50, ui.getLocation().y + 50);
                   opg.setVisible(true);
                   if (opg.isOK() == false) {
                      return false;
                   }
                }
             }

             // Make sure we have enough memory.
             // This code has not been maintained for a long time and probably
             // contains some errors. At the very least the nImages of the
             // Opener object should be updated. Should be phased out or updated.
             int nMasses = image.getNMasses();
             int nImages = image.getNImages();
             long memRequired = ((long) nMasses) * ((long) image.getWidth()) * ((long) image.getHeight()) * ((long) 2) * ((long) nImages);
             long maxMemory = IJ.maxMemory() - (128000000);
             for (int i = 0; i < nMasses; i++)
                bOpenMass[i] = true;
             while (memRequired > maxMemory) {
                ij.gui.GenericDialog gd = new ij.gui.GenericDialog("File Too Large");
                long aMem = memRequired;
                int canOpen = nImages;
                while (aMem > maxMemory) {
                   canOpen--;
                   aMem = nMasses * image.getWidth() * image.getHeight() * 2 * canOpen;
                }
                String[] names = image.getMassNames();
                for (int i = 0; i < image.getNMasses(); i++) {
                   String msg = "Open mass " + names[i];
                   gd.addCheckbox(msg, bOpenMass[i]);
                }
                gd.addNumericField("Open only ", (double) canOpen, 0, 5, " of " + image.getNImages() + " Images");
                gd.showDialog();
                if (gd.wasCanceled()) {
                   image = null;
                   return false;
                }
                nMasses = 0;
                for (int i = 0; i < image.getNMasses(); i++) {
                   bOpenMass[i] = gd.getNextBoolean();
                   if (bOpenMass[i]) {
                      nMasses++;
                   }
                }
                nImages = (int) gd.getNextNumber();
                memRequired = memRequired = ((long) nMasses) * ((long) image.getWidth()) * ((long) image.getHeight()) * ((long) 2) * ((long) nImages);
             }

             // Opens the first plane.
             int n = 0;
             int t = image.getNMasses() * (nImages);
             for (int i = 0; i < image.getNMasses(); i++) {
                if (isCancelled()) {
                   return false;
                }
                progress = 100 * n++ / t;
                setProgress(Math.min(progress, 100));
                if (bOpenMass[i]) {
                   MimsPlus mp = new MimsPlus(ui, i);
                   mp.setAllowClose(false);
                   massImages[i] = mp;
                   if (mp != null) {
                      massImages[i].getProcessor().setMinAndMax(0, 0);
                      massImages[i].getProcessor().setPixels(image.getPixels(i));
                   }
                }
             }
             updateStatus("1 of " + nImages);
             if (nImages <= 1)
                return true;

             // Appends additional planes.
             //
             // Yes, this code is kind ugly :(
             // and Yes, this code works :)
             for (int i = 1; i < nImages; i++) {
                boolean stop = false;
                if (isCancelled())
                   return false;
                image.setStackIndex(i);
                for (int mass = 0; mass < image.getNMasses(); mass++) {
                   progress = 100 * n++ / t;
                   setProgress(Math.min(progress, 100));
                   if (bOpenMass[mass]) {
                      if (stop) {
                         massImages[mass].appendBlankImage(i);
                      } else {
                         try {
                            massImages[mass].appendImage(i);
                         } catch (IOException ioe) {
                            if (mass == 0) {
                               stop = true;
                               break;
                            }
                            else if(isIM) {
                               stop = true;
                               massImages[mass].appendBlankImage(i);
                            }
                            else if(isNRRD) {
                               massImages[mass].appendBlankImage(i);
                            }
                         }
                      }
                   }
                }
                //here the divider ensures that the progress bar is updated no more than 10 times (thus why we divide by 10 and round)
                int divider = Math.max(Math.round((nImages+1)/10), 1);
                if ((i+1)% divider== 0) {
                    updateStatus((i + 1) + " of " + nImages);
                }
                if (stop) {
                   image.setNImages(i + 1);
                   break;
                }
             }
             long endTime = System.nanoTime();
             long duration = endTime - startTime;
             System.out.println(duration);
          } catch (Exception e) {
             if (!ui.silentMode) {
                IJ.error("Failed to open " + file + ":" + e.getMessage() + "\n");
                e.printStackTrace();
             } else {
                e.printStackTrace();
             }
             return false;
          }
          return true;
       }

    @Override
    public void done() {
      setCursor(null); //turn off the wait cursor
      stopButton.setEnabled(false);
      setProgress(0);
      currentlyOpeningImages = false;
     
    }

        public void doneLoadingFile() {
            if (!sessionOpened){
                Toolkit.getDefaultToolkit().beep();

                jProgressBar1.setValue(0);


                if (ui.single_instance_mode == true) {
                    java.awt.Point p = ui.getLocation();
                    java.awt.Point q = IJ.getInstance().getLocation();

                    OMLOGGER.finer("Ui screen location = " + p.getX() + "," + p.getY());
                    OMLOGGER.finer("Ui screen location = " + q.getX() + "," + q.getY());

                    ui.setVisible(false);
                    ui.setLocation(p);
                    ui.setVisible(true);
                    ui.setExtendedState(javax.swing.JFrame.NORMAL);
                    ui.show();

                    OMLOGGER.finer("ui location after: " + ui.getLocation());

                    IJ.getInstance().setVisible(false);
                    IJ.getInstance().setLocation(q);
                    IJ.getInstance().setVisible(true);
                    IJ.getInstance().setExtendedState(java.awt.Frame.NORMAL);

                }


                for (int i = 0; i < image.getNMasses(); i++) {
                    if (bOpenMass[i]) {
                        if (image.getNImages() > 1) {
                            massImages[i].setIsStack(true);
                            massImages[i].setSlice(1);
                        }
                        if (isSilentMode() == false) {
                            massImages[i].show();
                        }
                    }
                }

                if (isSilentMode() == false) {
                    if (wpMap.size() > 0 || previousFileCanceled == false) {
                        applyWindowState();
                    } else {
                        tileWindows();
                    }
                }

                for (int i = 0; i < image.getNMasses(); i++) {
                    if (bOpenMass[i]) {
                        massImages[i].addListener(ui);
                    } else {
                        massImages[i] = null;
                    }
                }

                jTabbedPane1.setEnabled(true);
                if (mimsData == null) {
                    initializeViewMenu();
                    mimsData = new com.nrims.MimsData(ui, image);
                    hsiControl = new MimsHSIView(ui);
                    mimsLog = new MimsLog();
                    mimsStackEditing = new MimsStackEditor(ui, image);
                    mimsTomography = new MimsTomography(ui);
                    mimsAction = new MimsAction(image);
                    segmentation = new SegmentationForm(ui);

                    jTabbedPane1.setComponentAt(0, mimsData);
                    jTabbedPane1.setTitleAt(0, "MIMS Data");
                    jTabbedPane1.add("Process", hsiControl);
                    jTabbedPane1.add("Contrast", cbControl);
                    jTabbedPane1.add("Stack Editing", mimsStackEditing);
                    jTabbedPane1.add("Tomography", mimsTomography);
                    jTabbedPane1.add("Segmentation", segmentation);
                    jTabbedPane1.add("MIMS Log", mimsLog);

                } else {
                    resetViewMenu();
                    mimsData = new com.nrims.MimsData(ui, image);
                    cbControl = new MimsCBControl(ui);
                    mimsStackEditing = new MimsStackEditor(ui, image);
                    int[] indices = mimsTomography.getSelectedStatIndices();
                    mimsTomography = new MimsTomography(ui);
                    mimsTomography.setSelectedStatIndices(indices);
                    mimsAction = new MimsAction(image);
                    segmentation = new SegmentationForm(ui);

                    jTabbedPane1.setComponentAt(0, mimsData);
                    jTabbedPane1.setTitleAt(0, "MIMS Data");
                    jTabbedPane1.setComponentAt(1, hsiControl);
                    jTabbedPane1.setComponentAt(2, cbControl);
                    jTabbedPane1.setComponentAt(3, mimsStackEditing);
                    jTabbedPane1.setComponentAt(4, mimsTomography);
                    jTabbedPane1.setComponentAt(5, segmentation);

                    mimsData.setMimsImage(image);
                    hsiControl.updateImage(false);
                }

                jTabbedPane1.addChangeListener(new ChangeListener() {
                    public void stateChanged(ChangeEvent e) {
                        int selected = jTabbedPane1.getSelectedIndex();
                        if (selected == 2) {
                            cbControl.updateHistogram();
                        }
                    }
                });

                mimsLog.Log("\n\nNew image: " + getImageFilePrefix());
                mimsLog.Log(ImageDataUtilities.getImageHeader(image));

                // Calculate theoretical duration
                double duration = image.getCountTime() * (double) image.getNImages();
                mimsLog.Log("Theoretical duration (s): " + Double.toString(duration));

                mimsTomography.resetImageNamesList();
                mimsStackEditing.resetSpinners();

                openers.clear();
                String fName = file.getName();
                openers.put(fName, image);

                // Add the windows to the combobox in CBControl.
                MimsPlus[] mp = getOpenMassImages();
                for (int i = 0; i < mp.length; i++) {
                    cbControl.addWindowtoList(mp[i]);
                }
                previousFileCanceled = false;
            }else{
                sessionOpened = false;
            }
        }

      private void openProcessFailedOrCanceled() {         
         closeCurrentImage();
         jTabbedPane1.setEnabled(false);
         previousFileCanceled = true;
         currentlyOpeningImages = false;
      }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    public javax.swing.JCheckBoxMenuItem DTCorrectionMenuItem;
    public javax.swing.JCheckBoxMenuItem QSACorrectionMenuItem;
    private javax.swing.JMenuItem RecomputeAllMenuItem;
    private javax.swing.JMenuItem aboutMenuItem;
    private javax.swing.JMenuItem batch2nrrdMenuItem;
    private javax.swing.JMenuItem captureImageMenuItem;
    private javax.swing.JMenuItem closeAllHSIMenuItem;
    private javax.swing.JMenuItem closeAllRatioMenuItem;
    private javax.swing.JMenuItem closeAllSumMenuItem;
    private javax.swing.JMenu closeMenu;
    private javax.swing.JMenuItem compositeMenuItem;
    public javax.swing.JMenu correctionsMenu;
    private javax.swing.JMenu editMenu;
    private javax.swing.JMenuItem emptyTestMenuItem;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenuItem exportPNGjMenuItem;
    private javax.swing.JMenuItem exportQVisMenuItem;
    private javax.swing.JMenu exportjMenu;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenuItem genStackMenuItem;
    private javax.swing.JMenuItem generateReportMenuItem;
    private javax.swing.JMenuItem imageNotesMenuItem;
    private javax.swing.JMenuItem importIMListMenuItem;
    private javax.swing.JMenuItem insertPicFrame;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenuItem jMenuItem1;
    private javax.swing.JMenuItem jMenuItem2;
    private javax.swing.JMenuItem jMenuItem9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPopupMenu jPopupMenu1;
    private javax.swing.JProgressBar jProgressBar1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JSeparator jSeparator7;
    private javax.swing.JSeparator jSeparator8;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JMenuItem openNewDraw;
    private javax.swing.JMenuItem openNewImpress;
    private javax.swing.JMenuItem openNewMenuItem;
    private javax.swing.JMenuItem openNewWriter;
    private javax.swing.JMenuItem openNextMenuItem;
    private javax.swing.JMenuItem preferencesMenuItem;
    private javax.swing.JMenuItem restoreMimsMenuItem;
    private javax.swing.JMenuItem roiManagerMenuItem;
    private javax.swing.JMenuItem saveMIMSjMenuItem;
    public javax.swing.JButton stopButton;
    private javax.swing.JMenuItem sumAllMenuItem;
    private javax.swing.JMenu testingMenu;
    private javax.swing.JMenuItem tileWindowsMenuItem;
    private javax.swing.JMenu utilitiesMenu;
    private javax.swing.JMenu viewMenu;
    // End of variables declaration//GEN-END:variables
}
