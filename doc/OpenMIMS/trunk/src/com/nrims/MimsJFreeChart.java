package com.nrims;

import com.nrims.plot.MimsChartFactory;
import com.nrims.plot.MimsChartPanel;
import com.nrims.plot.MimsXYPlot;
import ij.IJ;
import ij.gui.*;
import ij.process.*;
import java.awt.Color;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.util.ResourceBundleWrapper;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ExtensionFileFilter;
import org.jfree.ui.TextAnchor;

/**
 * MimsJFreeChart class creates a frame containing a <code>MimsXYPlot</code>.
 * This class is used to create frames that contain plots, usually
 * for statistical data associated with images.
 *
 * @author zkaufman
 */
public class MimsJFreeChart extends JFrame implements WindowListener, MouseListener {

   private String[] stats;
   private MimsPlus images[];
   private Roi[] rois;
   private ArrayList planes;
   private com.nrims.UI ui;
   //private MimsChartPanel chartpanel;
   private MimsChartPanel chartpanel;
   
   public MimsJFreeChart(UI ui) {
      super("Plot");
      this.ui = ui;
      JMenuBar menuBar = new JMenuBar();
      JMenu menu;
      JMenuItem menuItem;
      menu = new JMenu("File");
      menuBar.add(menu);
      
      addMouseListener(this);

      // Save as menu item.
      menuItem = new JMenuItem("Save");
      menuItem.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveAs();
         }
      });
      menu.add(menuItem);

      // Generate report menu item.
      menuItem = new JMenuItem("Generate Report");
      menuItem.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            generateReport();
         }
      });
      menu.add(menuItem);

      // Generate frame.
      setJMenuBar(menuBar);
      pack();
      setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
   }

  /**
   * Plots the data and shows the frame. Only call this method
   * if all relevant member variables are set.
   *
   * @param appendingData <code>true</code> if appending a plot
   * to an existing frame, otherwise <code>false</code>.
   */
   public void plotData(boolean appendingData) {

      // Add data to existing plaot if appending.
      if (appendingData && chartpanel != null)
         appendData();      
      else {
         
         // Create an chart empty.
         JFreeChart chart = createChart();

         // Get the data.
         XYDataset xydata = getDataset();

         // Apply data to the plot
         MimsXYPlot xyplot = (MimsXYPlot)chart.getPlot();
         xyplot.setDataset(xydata);

         // Generate the layout.
         //chartpanel = new MimsChartPanel(chart);
         chartpanel = new MimsChartPanel(chart);
         chartpanel.addMouseListener(this);
         chartpanel.setPreferredSize(new java.awt.Dimension(600, 400));
         String lastFolder = ui.getLastFolder();
         if (lastFolder != null) {
            if (new File(lastFolder).exists()) {
               chartpanel.setDefaultDirectoryForSaveAs(new File(lastFolder));
            }
         }
         this.add(chartpanel);

         // Add menu item for showing/hiding crosshairs.
         JMenuItem xhairs = new JMenuItem("Show/Hide Crosshairs");
         xhairs.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               MimsJFreeChart.showHideCrossHairs(chartpanel);
            }
         });
         chartpanel.getPopupMenu().addSeparator();
         chartpanel.getPopupMenu().add(xhairs);

         // Add menu item for toggling between linear and log scales.
         JMenuItem logscale = new JMenuItem("Log/Linear scale");
         logscale.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               MimsJFreeChart.logLinScale(chartpanel);
            }
         });
         chartpanel.getPopupMenu().add(logscale);

         // Add menu item for exporting plot to report.
         JMenuItem genreport = new JMenuItem("Generate Report");
         genreport.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               generateReport();
            }
         });
         chartpanel.getPopupMenu().add(genreport);

         // Replace Save As... menu item.
         chartpanel.getPopupMenu().remove(3);
         JMenuItem saveas = new JMenuItem("Save as...");
         saveas.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               saveAs();
            }
         });
         chartpanel.getPopupMenu().add(saveas, 3);
         
         // Add an option for getting the underlying data
          JMenuItem asTextMenuItem = new javax.swing.JMenuItem("Display text");
          asTextMenuItem.addActionListener(new ActionListener() {
              public void actionPerformed(ActionEvent e) {
                  displayProfileData();
              }
          });
          chartpanel.getPopupMenu().add(asTextMenuItem, 2);
        
         // Add key listener.
         KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            public boolean dispatchKeyEvent(KeyEvent e) {
               if (e.getID() == KeyEvent.KEY_PRESSED && thisHasFocus()) {
                  chartpanel.keyPressed(e);
               }
               return false;
            }
         });

         pack();
         setVisible(true);

      }
   }

   private boolean thisHasFocus(){
      return this.hasFocus();
   }

/**
 * Contructs the frame and sets the specifics regarding visual parameters.
 */
   private static JFreeChart createChart() {
      JFreeChart chart = MimsChartFactory.createMimsXYLineChart("", "Plane", "", null, PlotOrientation.VERTICAL, true, true, false);
      chart.setBackgroundPaint(Color.white);
      // Get a reference to the plot.
      MimsXYPlot plot = (MimsXYPlot) chart.getPlot();

      // Create integer x-axis.
      plot.getDomainAxis().setStandardTickUnits(NumberAxis.createIntegerTickUnits());

      // Set colors.
      plot.setBackgroundPaint(Color.lightGray);
      plot.setDomainGridlinePaint(Color.white);
      plot.setRangeGridlinePaint(Color.white);

      // Movable range and domain.
      plot.setDomainPannable(true);
      plot.setRangePannable(true);

      // Allow crosshairs to 'focus' in on a given point.
      plot.setDomainCrosshairVisible(true);
      plot.setRangeCrosshairVisible(true);            

      return chart;
   }

  /**
   * Append data to existing plot.
   */
   private void appendData() {
         XYDataset tempdata = getDataset();

         XYSeriesCollection originaldata = (XYSeriesCollection) chartpanel.getChart().getXYPlot().getDataset();
         XYSeriesCollection newdata = (XYSeriesCollection) getDataset();

         addToDataSet(newdata, originaldata);
         tempdata = missingSeries(newdata, originaldata);

         XYSeriesCollection foo = (XYSeriesCollection) tempdata;
         int n = foo.getSeriesCount();
         for (int i = 0; i < n; i++) {
            originaldata.addSeries(foo.getSeries(i));
         }
         nullMissingPoints((XYSeriesCollection) chartpanel.getChart().getXYPlot().getDataset());
   }

  /**
   * Add any series with a new key in newdata to olddata.
   */
   private boolean addToDataSet(XYSeriesCollection newdata, XYSeriesCollection olddata) {

      boolean hasnewseries = false;
      // Loop over newdata.
      for (int nindex = 0; nindex < newdata.getSeriesCount(); nindex++) {
         XYSeries newseries = newdata.getSeries(nindex);
         String newname = (String) newseries.getKey();
         // Check if olddata has series with same key.
         XYSeries oldseries = null;
         try {
            oldseries = olddata.getSeries(newname);
         } catch (org.jfree.data.UnknownKeyException e) {
            hasnewseries = true;
            continue;
         }
         if (oldseries != null) {

            for (int n = 0; n < newseries.getItemCount(); n++) {
               // Remove possible {x,null} pairs.
               double xval = (Double) newseries.getX(n);
               int pos = oldseries.indexOf(xval);
               if ((pos > -1) && (oldseries.getY(pos) == null)) {
                  oldseries.remove(pos);
               }
               oldseries.add(newseries.getDataItem(n));
            }
         }
      }
      return hasnewseries;
   }

  /**
   * Return any series from newdata with a key missing from olddata.
   */
   private XYSeriesCollection missingSeries(XYSeriesCollection newdata, XYSeriesCollection olddata) {
      XYSeriesCollection returncollection = new XYSeriesCollection();

      // Loop over newdata.
      for (int nindex = 0; nindex < newdata.getSeriesCount(); nindex++) {
         XYSeries newseries = newdata.getSeries(nindex);
         String newname = (String) newseries.getKey();
         // Check if olddata has series with same key.
         XYSeries oldseries = null;
         try {
            oldseries = olddata.getSeries(newname);
         } catch (org.jfree.data.UnknownKeyException e) {
            returncollection.addSeries(newseries);
            continue;
         }
      }

      return returncollection;
   }

  /**
   * Adds the pair {x, null} to any series in data that is missing {x, y}.
   * Changes contents of data.
   */
      private void nullMissingPoints(XYSeriesCollection data) {
      for (int nindex = 0; nindex < data.getSeriesCount(); nindex++) {
         XYSeries series = data.getSeries(nindex);
         double min = series.getMinX();
         double max = series.getMaxX();
         for (int xindex = (int) min; xindex <= (int) max; xindex++) {
            double xval = (double) xindex;
            int pos = series.indexOf(xval);
            if (pos < 0) {
               series.add(xval, null);
            }
         }
      }
   }

  /**
   * This method will generate a set of plots for a given set of: rois, stats, images.
   *
   * @return XYDataset
   */
   public XYDataset getDataset() {

      // Initialize some variables
      XYSeriesCollection dataset = new XYSeriesCollection();
      XYSeries series[][][] = new XYSeries[rois.length][images.length][stats.length];
      String seriesname[][][] = new String[rois.length][images.length][stats.length];
      int currentSlice = ui.getOpenMassImages()[0].getCurrentSlice();
      ArrayList<String> seriesNames = new ArrayList<String>();
      String tempName = "";
      double stat;

      // Image loop
      for (int j = 0; j < images.length; j++) {
         MimsPlus image = images[j];

         // Plane loop
         for (int ii = 0; ii < planes.size(); ii++) {
            int plane = (Integer) planes.get(ii);
            if (image.getMimsType() == MimsPlus.MASS_IMAGE) {
               ui.getOpenMassImages()[0].setSlice(plane, false);
            } else if (image.getMimsType() == MimsPlus.RATIO_IMAGE) {
               ui.getOpenMassImages()[0].setSlice(plane, image);
            }

            // Roi loop
            for (int i = 0; i < rois.length; i++) {

               // Set the Roi to the image.
               Integer[] xy = ui.getRoiManager().getRoiLocation(rois[i].getName(), plane);
               rois[i].setLocation(xy[0], xy[1]);
               image.setRoi(rois[i]);

               // Stat loop
               for (int k = 0; k < stats.length; k++) {

                  // Generate a name for the dataset.
                  String prefix = "";
                  if (image.getType() == MimsPlus.MASS_IMAGE || image.getType() == MimsPlus.RATIO_IMAGE)
                        prefix = "_m";
                  if (seriesname[i][j][k] == null) {
                     tempName = stats[k] + prefix + image.getRoundedTitle(true) + "_r" + rois[i].getName();
                     int dup = 1;
                     while (seriesNames.contains(tempName)) {
                        tempName = stats[k] + prefix + image.getRoundedTitle(true) + "_r" + rois[i].getName() + " (" + dup + ")";
                        dup++;
                     }
                     seriesNames.add(tempName);
                     seriesname[i][j][k] = tempName;
                  }

                  // Add data to the series.
                  if (series[i][j][k] == null) {
                     series[i][j][k] = new XYSeries(seriesname[i][j][k]);
                  }
                  
                  // Get the statistic.
                  stat = getSingleStat(image, stats[k], ui);
                  if (stat > Double.MAX_VALUE || stat < (-1.0)*Double.MAX_VALUE)
                     stat = Double.NaN;
                  series[i][j][k].add(((Integer) planes.get(ii)).intValue(), stat);

               } // End of Stat
            } // End of Roi
         } // End of Plane
      } // End of Image

      // Populate the final data structure.
      for (int i = 0; i < rois.length; i++) {
         for (int j = 0; j < images.length; j++) {
            for (int k = 0; k < stats.length; k++) {
               dataset.addSeries(series[i][j][k]);
            }
         }
      }

      ui.getOpenMassImages()[0].setSlice(currentSlice);

      return dataset;
   }

   /**
    * Sets the images to be plotted.
    *
    * @param images a set of MimsPlus images.
    */
   public void setImages(MimsPlus[] images){
      this.images = images;
   }

   /**
    * Sets the statistics to be plotted.
    *
    * @param stats a set of statistics.
    */
   public void setStats(String[] stats) {
      this.stats = stats;
   }

   /**
    * Sets the ROIs to be plotted.
    *
    * @param rois a set of ROIs.
    */
   public void setRois(Roi[] rois) {
      this.rois = rois;
   }

   /**
    * Sets the planes to be plotted.
    *
    * @param planes an arraylist of planes.
    */
   public void setPlanes(ArrayList planes) {
      this.planes = planes;
   }

   /**
    * Display the reportGenerator object for generating user reports.
    */
   public void generateReport() {
      ReportGenerator rg = ui.getReportGenerator();
      if (rg == null || !rg.isVisible()) {
         ui.openReportGenerator();
         rg = ui.getReportGenerator();
      }
      Image img = getImage();
      if (img == null)
         return;
      rg.addImage(img, "Plot");
   }

   /**
    * Gets an AWT Image for insertion in report.
    *
    * @return the AWT Image.
    */
   public Image getImage() {
      MimsChartPanel mcp = getChartPanel();
      if (mcp == null)
         return null;
      BufferedImage img = mcp.getChart().createBufferedImage(mcp.getWidth(), mcp.getHeight());
      if (img == null)
         return null;
      return img;
   }

   /**
    * Returns the ChartPanel.
    *
    * @return the chartpanel.
    */
   public MimsChartPanel getChartPanel() {
      return chartpanel;
   }

   /**
    * Swap crosshairs from hidden to shown or vice versa
    * @param chartpanel GUI element to be affected
    */
   public static void showHideCrossHairs(MimsChartPanel chartpanel) {
      Plot plot = chartpanel.getChart().getPlot();
      if (!(plot instanceof MimsXYPlot))
         return;

      // Show/Hide XHairs
      MimsXYPlot xyplot = (MimsXYPlot) plot;
      xyplot.setDomainCrosshairVisible(!xyplot.isDomainCrosshairVisible());
      xyplot.setRangeCrosshairVisible(!xyplot.isRangeCrosshairVisible());
      xyplot.showXHairLabel(xyplot.isDomainCrosshairVisible() || xyplot.isDomainCrosshairVisible());
   }

   public void setLabel() {
       System.out.println("MimsJFreeChart.setLabel() called");
       
       MimsXYPlot plot = (MimsXYPlot)chartpanel.getChart().getPlot();
      if (plot.isDomainCrosshairVisible() && plot.isRangeCrosshairVisible() && plot.isCrosshairLabelVisible()) {
         double x = plot.getDomainCrosshairValue();
         double y = plot.getRangeCrosshairValue();
         double xmax = plot.getDomainAxis().getUpperBound();
         double ymax = plot.getRangeAxis().getUpperBound();
        	DecimalFormat twoDForm = new DecimalFormat("#.##");
         String xhairlabel = "x = " + Double.valueOf(twoDForm.format(x)) + ", y = " + Double.valueOf(twoDForm.format(y));
         plot.getCrossHairAnnotation().setText(xhairlabel);
         plot.getCrossHairAnnotation().setX(xmax);
         plot.getCrossHairAnnotation().setY(ymax);
         plot.getCrossHairAnnotation().setTextAnchor(TextAnchor.TOP_RIGHT);        
      } else {
         plot.getCrossHairAnnotation().setText("");
      }

   }
   
    /**
    * Change y axis from linear to log scale or vice versa
    * @param chartpanel GUI element to be affected
    */
    public static void logLinScale(MimsChartPanel chartpanel) {
      Plot plot = chartpanel.getChart().getPlot();

      if (!(plot instanceof MimsXYPlot))
         return;


      MimsXYPlot xyplot = (MimsXYPlot) plot;
      org.jfree.chart.axis.ValueAxis axis = xyplot.getRangeAxis();
      String label = axis.getLabel();

       if (!(axis instanceof org.jfree.chart.axis.LogarithmicAxis)) {
           org.jfree.chart.axis.LogarithmicAxis logaxis = new org.jfree.chart.axis.LogarithmicAxis(label);
           logaxis.setRange(axis.getLowerBound(), axis.getUpperBound());
           logaxis.setAutoRange(true);
           logaxis.setStrictValuesFlag(false);
           xyplot.setRangeAxis(logaxis);
       } else {
           org.jfree.chart.axis.NumberAxis linaxis = new org.jfree.chart.axis.NumberAxis(label);
           linaxis.setAutoRange(true);
           xyplot.setRangeAxis(linaxis);
       }
   }

   /**
    * Saveas the chart as a .png
    */
    public void saveAs(){
       MimsJFileChooser fileChooser = new MimsJFileChooser(ui);
       fileChooser.setSelectedFile(new File(ui.getLastFolder(), ui.getImageFilePrefix() + ".png"));
       ResourceBundle localizationResources = ResourceBundleWrapper.getBundle("org.jfree.chart.LocalizationBundle");
       ExtensionFileFilter filter = new ExtensionFileFilter(
               localizationResources.getString("PNG_Image_Files"), ".png");
       fileChooser.addChoosableFileFilter(filter);
       fileChooser.setFileFilter(filter);
       int option = fileChooser.showSaveDialog(chartpanel);
       if (option == MimsJFileChooser.APPROVE_OPTION) {
          String filename = fileChooser.getSelectedFile().getPath();
          if (!filename.endsWith(".png")) {
             filename = filename + ".png";
          }
          try {
             ChartUtilities.saveChartAsPNG(new File(filename), chartpanel.getChart(), getWidth(), getHeight());
          } catch (IOException ioe) {
             IJ.error("Unable to save file.\n\n" + ioe.toString());
          }
       }
    }

   /**
    * Create a table from the same roi/images/stats/planes
    * set as used to creazte the plot
    */
    public void displayProfileData() {
       MimsJTable table = new MimsJTable(ui);
       table.setPlanes(planes);
       table.setStats(stats);
       table.setRois(rois);
       table.setImages(images);
       table.createTable(false);
       table.showFrame();
    }

   /**
    * A static method that returns a double value for a specific
    * statistic. The <code> stats </code> parameter must be defined
    * and associated with an image, usually by calling <code>
    * image.getStatistics() </code>.
    *
    * @param stats image statistics object.
    * @param statname a string naming the desired statistic.
    * @param ui needed only as a check to see if we are in Percent turnover mode (wanted to avoid passing this but felt it necessary).
    * @return the statistic value (default value = -999).
    */
   public static double getSingleStat(MimsPlus image, String statname, UI ui) {

        ImageStatistics stats = image.getStatistics(MimsJTable.mOptions);

        if(statname.equals("area"))
            return stats.area;
        if(statname.equals("mean"))
            return stats.mean;
        if(statname.equals("stddev"))
            return stats.stdDev;
        if (statname.equals("N/D"))
            return getNoverDstat(image, ui);
        if(statname.equals("mode"))
            return stats.mode;
        if(statname.equals("min"))
            return stats.min;
        if(statname.equals("max"))
            return stats.max;
        if(statname.equals("xcentroid"))
            return stats.xCentroid;
        if(statname.equals("ycentroid"))
            return stats.yCentroid;
        if(statname.equals("xcentermass"))
            return stats.xCenterOfMass;
        if(statname.equals("ycentermass"))
            return stats.yCenterOfMass;
        if(statname.equals("roix"))
            return stats.roiX;
        if(statname.equals("roiy"))
            return stats.roiY;
        if(statname.equals("roiwidth"))
            return stats.roiWidth;
        if(statname.equals("roiheight"))
            return stats.roiHeight;
        if(statname.equals("major"))
            return stats.major;
        if(statname.equals("minor"))
            return stats.minor;
        if(statname.equals("angle"))
            return stats.angle;
        if(statname.equals("sum"))
            return (stats.pixelCount*stats.mean);
        if(statname.equals("median"))
            return stats.median;
        if(statname.equals("kurtosis"))
            return stats.kurtosis;

        return -999;
    }

   /**
    * Computes N/D statistic. 
    */
   private static double getNoverDstat(MimsPlus image, UI ui) {
      double sf = 10000.0;
      double returnVal = -999;
      Roi roi = image.getRoi();

      boolean isRatio = (image.getMimsType() == MimsPlus.RATIO_IMAGE);
      boolean isSum = (image.getMimsType() == MimsPlus.SUM_IMAGE);
      boolean isSumRatio = false;

      if (isSum) {
         SumProps sp = image.getSumProps();
         isSumRatio = (sp.getSumType() == MimsPlus.RATIO_IMAGE);
      }

      if (isRatio || isSumRatio) {
         // Get scale factor.
         if (image.getMimsType() == MimsPlus.HSI_IMAGE) {
            sf = image.getHSIProps().getRatioScaleFactor();
         } else if (image.getMimsType() == MimsPlus.RATIO_IMAGE) {
            sf = image.getRatioProps().getRatioScaleFactor();
         }

         // Calculate the statistic.
         MimsPlus mp_num = image.internalNumerator;
         mp_num.setRoi(roi);
         double num = mp_num.getStatistics().mean;

         MimsPlus mp_den = image.internalDenominator;
         mp_den.setRoi(roi);
         double den = mp_den.getStatistics().mean;

         returnVal = (double) sf*(num)/(den);
         if (ui.getIsPercentTurnover()) {            
            float reference = ui.getPreferences().getReferenceRatio();
            float background = ui.getPreferences().getBackgroundRatio();
            returnVal = HSIProcessor.turnoverTransform((float) returnVal, reference, background, (float)sf);
         }         
      }
      return returnVal;
   }
  

   @Override
   public void windowActivated(WindowEvent e) {}

   public void windowOpened(WindowEvent e) {}

   public void windowClosing(WindowEvent e) {}

   public void windowClosed(WindowEvent e) {}

   public void windowIconified(WindowEvent e) {}

   public void windowDeiconified(WindowEvent e) {}

   public void windowDeactivated(WindowEvent e) {}

    @Override
    public void mouseClicked(MouseEvent e) {
        //this.setLabel();
        //((MimsXYPlot)this.chartpanel.getChart().getPlot()).setCrosshairLabel();

        ReportGenerator rg = ui.getReportGenerator();
        if (rg != null && rg.isVisible() && e.isControlDown()) {
            Image img = getImage();
            if (img != null) {
                rg.addImage(img, "Plot");
            }
        }
    }
    public void mousePressed(MouseEvent e) {}

    public void mouseReleased(MouseEvent e) {}

    public void mouseEntered(MouseEvent e) {}

    public void mouseExited(MouseEvent e) {}
}
