/*
 * ============================================================================
 * JGribX
 * ============================================================================
 * Written by Andrew Spiteri <andrew.spiteri@um.edu.mt>
 * Adapted from JGRIB: http://jgrib.sourceforge.net/
 * 
 * Licensed under MIT: https://github.com/spidru/JGribX/blob/master/LICENSE
 * ============================================================================
 */
package mt.edu.um.cf2.jgribx.grib2;

import mt.edu.um.cf2.jgribx.GribCodes;
import mt.edu.um.cf2.jgribx.GribInputStream;
import mt.edu.um.cf2.jgribx.Logger;
import mt.edu.um.cf2.jgribx.NotSupportedException;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * A class representing the product definition section (PDS) of a GRIB record.
 */
public class Grib2RecordPDS
{
    protected class Layer
    {
        private Grib2Level level1;
        private Grib2Level level2;

        public Layer(Grib2Level level1, Grib2Level level2)
        {
            this.level1 = level1;
            this.level2 = level2;
        }

        public Grib2Level getFirstLevel()
        {
            return level1;
        }

        public Grib2Level getSecondLevel()
        {
            return level2;
        }

        public float[] getValues()
        {
            return new float[] {level1.getValue(), level2.getValue()};
        }

        public boolean isSingleLayer()
        {
            return level2 == null;
        }

        @Override
        public String toString()
        {
            return "Layer [" + level1.getDescription() + " - " + level2.getDescription() + "]";
        }
    }
    /**
     * The time at which the forecast applies.
     */
    private Calendar forecastTime;

    /**
     * The forecast end time, if an accumulation. Otherwise, same as forecastTime
     */
    private Calendar endTime;
    
    /**
     * Length in bytes of this PDS.
     */
    protected int length;

    /**
     * The parameter in the form of {@link Grib2Parameter}.
     */
    protected Grib2Parameter parameter;
    
    /**
     * Analysis or forecast generating process identified.
     * <br>
     * e.g. 81 (Analysis from GFS)
     */
    private int genProcessId;
    
    /**
     * Type of generating process.
     * <br>
     * e.g. 2 (Forecast)
     */
    private int genProcessType;
    
    private int nCoords;
    
    private int paramCategory;

    private Layer layer;
    
    /**
     * Parameter Number (used when the appropriate parameter is not found)
     */
    private int paramNumber;
    
    private int templateId;
    
    /**
     * Constructs a {@link Grib2RecordPDS} object from a bit input stream.
     *
     * @param in bit input stream with PDS content
     * @param discipline
     * @param referenceTime
     *
     * @throws IOException if stream can not be opened etc.
     * @throws NotSupportedException
     */
    public Grib2RecordPDS(GribInputStream in, ProductDiscipline discipline, Calendar referenceTime) throws NotSupportedException, IOException
    {
        /* [1-4] Section Length */
        length = in.readUINT(4);

        /* [5] Section Number */
        int section = in.readUINT(1);
        if (section != 4)
            Logger.println("PDS section contains an invalid section number", Logger.WARNING);
        
        /* [6-7] Number of coordinate values after template */
        nCoords = in.readUINT(2);
        if (nCoords > 0)
        {
            throw new NotSupportedException("Hybrid coordinate values are not yet supported");
        }
        
        /* [8-9] Template number */
        templateId = in.readUINT(2);

        // 0 = Analysis or forecast at a horizontal level or layer at a point in time
        // 8 = Average, accumulation, extreme values or other statistically processed values at a horizontal level or in a horizontal layer in a continuous or non-continuous time interval
        if (templateId == 0 || templateId == 8) {
            /* [10] Parameter category */
            paramCategory = in.readUINT(1);

            /* [11] Parameter number */
            paramNumber = in.readUINT(1);

            if (!Grib2Parameter.isDefaultLoaded())
                Grib2Parameter.loadDefaultParameters();

            parameter = Grib2Parameter.getParameter(discipline, paramCategory, paramNumber);
            if (parameter == null)
            {
                throw new NotSupportedException("Unsupported parameter: D:" + discipline + " C:" + paramCategory
                        + " N:" + paramNumber);
            }

            /* [12] Type of generating process */
            genProcessType = in.readUINT(1);

            /* [13] Background generating process identifier (defined by originating centre) */
            int backgroundGeneratingProcessId = in.readUINT(1);

            /* [14] Analysis or forecast generating process identifier */
            genProcessId = in.readUINT(1);

            /* [15-16] Hours of observational data cutoff after reference time (see Note) */
            int observationalHours = in.readUINT(2);

            /* [17] Minutes of observational data cutoff after reference time (see Note) */
            in.readUINT(1);

            /* [18] Indicator of unit of time range (see Code table 4.4) */
            int timeRangeUnitIndicator = in.readUINT(1);

            /* [19-22] Forecast time in units defined in octet 18 */
            int forecastTimeAhead = in.readUINT(4);

            forecastTime = (Calendar) referenceTime.clone();
            switch (timeRangeUnitIndicator)
            {
                case 0:
                    // Minute
                    forecastTime.add(Calendar.MINUTE, forecastTimeAhead);
                    break;
                case 1:
                    // Hour
                    forecastTime.add(Calendar.HOUR_OF_DAY, forecastTimeAhead);
                    break;
                case 2:
                    // Day
                    forecastTime.add(Calendar.DAY_OF_MONTH, forecastTimeAhead);
                    break;
                case 3:
                    // Month
                    forecastTime.add(Calendar.MONTH, forecastTimeAhead);
                    break;
                case 4:
                    // Year
                    forecastTime.add(Calendar.YEAR, forecastTimeAhead);
                    break;
                case 5:
                    // Decade
                    forecastTime.add(Calendar.YEAR, forecastTimeAhead*10);
                    break;
                case 6:
                    // Normal (30 years)
                    forecastTime.add(Calendar.YEAR, forecastTimeAhead*30);
                    break;
                case 7:
                    // Century
                    forecastTime.add(Calendar.YEAR, forecastTimeAhead*100);
                    break;
                case 10:
                    // 3 Hours
                    forecastTime.add(Calendar.HOUR_OF_DAY, forecastTimeAhead*3);
                    break;
                case 11:
                    // 6 Hours
                    forecastTime.add(Calendar.HOUR_OF_DAY, forecastTimeAhead*6);
                    break;
                case 12:
                    // 12 Hours
                    forecastTime.add(Calendar.HOUR_OF_DAY, forecastTimeAhead*12);
                    break;
                case 13:
                    // Second
                    forecastTime.add(Calendar.SECOND, forecastTimeAhead);
                    break;
                default:
                    throw new NotSupportedException("Time range " + timeRangeUnitIndicator + " is not supported yet");
            }

            /* [23] Type of first fixed surface (see Code table 4.5) */
            int level1Type = in.readUINT(1);

            /* [24] Scale factor of first fixed surface */
            int level1ScaleFactor = in.readUINT(1);

            /* [25-28] Scaled value of first fixed surface */
            int level1ScaledValue = in.readUINT(4);

            /* [29] Type of second fixed surface */
            int level2Type = in.readUINT(1);

            /* [30] Scale factor of second fixed surface */
            int level2ScaleFactor = in.readUINT(1);

            /* [31-34] Scaled value of second fixed surface */
            int level2ScaledValue = in.readUINT(4);

            ///////////////////////////////////////////////////////////////
            /* PROCESSING */
            int[] scaledValues = {level1ScaledValue, level2ScaledValue};
            int[] scaleFactors = {level1ScaleFactor, level2ScaleFactor};
            int[] types = {level1Type, level2Type};
            float[] levelValues = new float[2];
            Grib2Level[] levels = new Grib2Level[2];

            for (int i = 0; i < levelValues.length; i++)
            {
                levelValues[i] = scaledValues[i] / (float) Math.pow(10, scaleFactors[i]);
                levels[i] = Grib2Level.getLevel(types[i], levelValues[i]);
                if (levels[i] == null && types[i] != GribCodes.MISSING)
                {
                    throw new NotSupportedException("Unsupported level of type " + types[i]);
                }
            }
            layer = new Layer(levels[0], levels[1]);
            endTime = (Calendar) forecastTime.clone();
        } else {
            throw new NotSupportedException("Unsupported template number: " + templateId);
        }

        if (templateId == 8) {
            /* [35-36] Year of end of overall time interval */
            var endYear = in.readUINT(2);

            /* [37] Month of end of overall time interval */
            var endMonth = in.readUINT(1);

            /* [38] Day of end of overall time interval */
            var endDay = in.readUINT(1);

            /* [39] Hour of end of overall time interval */
            var endHour = in.readUINT(1);

            /* [40] Minute of end of overall time interval */
            var endMinute = in.readUINT(1);

            /* [41] Second of end of overall time interval */
            var endSecond = in.readUINT(1);

            /* [42] Number of time range */
            in.readUINT(1);

            /* [43-46] Number of missing in statistical process */
            in.readUINT(4);

            /* [47] Type of statistical processing */
            in.readUINT(1);

            /* [48] Type of time increment */
            in.readUINT(1);

            /* [49] Unit for time range */
            in.readUINT(1);

            /* [50-53] Length of time range */
            in.readUINT(4);

            /* [54] Unit for time increment */
            in.readUINT(1);

            /* [55-58] Time increment */
            in.readUINT(4);

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            endTime = new GregorianCalendar(endYear, endMonth, endDay, endHour, endMinute, endSecond);
        }
    }
    
    /**
     * Returns the forecast time.
     * @return 
     */
    public Calendar getForecastTime()
    {
        return forecastTime;
    }

    /**
     * Returns the end time.
     * @return
     */
    public Calendar getEndTime() {return endTime; }
    
    public String getGeneratingProcessType()
    {
        switch (genProcessType)
        {
            case 0:
                return "Analysis";
            case 1:
                return "Initialisation";
            case 2:
                return "Forecast";
            case 3:
                return "Bias Corrected Forecast";
            case 4:
                return "Ensemble Forecast";
            default:
                return "Unsupported";
        }
    }

    /**
     * Get the byte length of this section.
     *
     * @return length in bytes of this section
     */
    public int getLength()
    {

        return this.length;
    }
    
    /**
     * Get the number of coordinates.
     * @return 
     */
    public int getNumberOfCoordinates()
    {
        return nCoords;
    }
    
    /**
     * Get the abbreviation representing the parameter.
     * @return the abbreviation
     */
    public String getParameterAbbrev()
    {
        if (parameter == null)
            return "";
        else
            return parameter.getCode();
    }
    
    /**
     * Get a description of the parameter.
     *
     * @return description of parameter
     */
    public String getParameterDescription()
    {
        if (parameter == null)
            return "Unknown Parameter: Category "+paramCategory+" Number "+this.paramNumber;
        return this.parameter.getDescription();
    }
    
    public String getParameterUnits()
    {
        return parameter.getUnits();
    }
    
    public Layer getLayer()
    {
        return layer;
    }
    
    public String getLayerDescription()
    {
        return layer.toString();
    }

    public String getLevelCode()
    {
        String code = layer.getFirstLevel().getCode();
        if (!layer.isSingleLayer() && !code.equals(layer.getSecondLevel().getCode()))
        {
            Logger.println("Layer contains different level types. Only showing first level", Logger.WARNING);
        }
        return code;
    }

    public String getLevelDescription()
    {
        String desc = "";
        if (layer.isSingleLayer())
        {
            desc = layer.getFirstLevel().getDescription();
        }
        else
        {
            desc = "Layer from (" + layer.getFirstLevel().getDescription() + ") down to (" + layer.getSecondLevel().getDescription() + ")";
        }
        return desc;
    }
    
    public String getLevelIdentifier()
    {
        if (!layer.isSingleLayer())
        {
            Logger.println("Layer contains two levels. Only showing first level", Logger.WARNING);
        }
        return layer.getFirstLevel().getLevelIdentifier();
    }

    /**
     * Get the parameter for this pds.
     *
     * @return date and time
     */
    public Grib2Parameter getParameter() {
        return this.parameter;
    }
    
    public int getProcessId()
    {
        return genProcessId;
    }

    /**
     * Get a string representation of this PDS.
     *
     * @return string representation of this PDS
     */
    @Override
    public String toString() {
        return "        Abbrev: " + getParameterAbbrev() + "\n"
                + "        Description: " + getParameterDescription() + "\n"
                + "        Units: " + getParameterUnits() + "\n"
                + "        Type: " + getGeneratingProcessType() + "\n";
    }

    /**
     * rdg - added an equals method here
     *
     * @param obj - to test
     * @return true/false
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Grib2RecordPDS)) {
            return false;
        }
        if (this == obj) {
            // Same object
            return true;
        }
        Grib2RecordPDS pds = (Grib2RecordPDS) obj;

        if (length != pds.length) {
            return false;
        }

        if (!(parameter.equals(pds.getParameter()))) {
            return false;
        }

        return true;

    }

}
