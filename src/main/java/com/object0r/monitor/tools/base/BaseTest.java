package com.object0r.monitor.tools.base;

import com.object0r.monitor.tools.Manager;
import com.object0r.monitor.tools.datatypes.HistoricValue;
import com.object0r.monitor.tools.datatypes.SshConnectionData;
import com.object0r.monitor.tools.datatypes.TimeInterval;
import com.object0r.monitor.tools.helpers.HistoricValuesManager;
import com.object0r.toortools.ConsoleColors;
import com.object0r.toortools.helpers.DateHelper;
import com.object0r.toortools.os.OsCommandOutput;
import com.object0r.toortools.os.OsHelper;

import java.util.*;
import java.util.concurrent.TimeUnit;

public abstract class BaseTest extends Thread
{
    private boolean forceRun = false;
    private String testReportPrefix = "osm - ";
    //protected abstract TimeInterval getRunEvery();

    protected boolean sendAggregated = false;

    protected TimeInterval getRunEvery()
    {
        return new TimeInterval(1, TimeUnit.HOURS);
    }


    public BaseTest(BaseReporter reporters, boolean forceRun)
    {
        this(reporters);
        this.forceRun = forceRun;
    }

    public BaseTest(BaseReporter reporter)
    {
        reporters.add(reporter);
    }

    private Vector<BaseReporter> reporters = new Vector<BaseReporter>();

    public Vector<String> errors = new Vector<String>();


    public abstract String getTestName();

    public void run()
    {
        if (!shouldRun() && !forceRun)
        {
            return;
        }
        System.out.println("Running " + getTestName() + " (every " + getRunEvery().getCount() + " " + getRunEvery().getTimeUnit() + ")");
        //errors = runTests();
        errors = baseRunTests();
        if (errors.size() > 0)
        {
            ConsoleColors.printRed("Sending errors:");
        }

        //clean duplicates
        Set<String> set = new HashSet<String>();
        set.addAll(errors);
        errors.clear();
        errors.addAll(set);
        String separator = "\n----------------------\n";
        StringBuffer aggregatedErrors = new StringBuffer("");
        for (String error : errors)
        {
            System.out.println(error);
            for (BaseReporter reporter : reporters)
            {
                aggregatedErrors.append(error);
                aggregatedErrors.append(separator);
                if (!sendAggregated)
                {
                    reporter.report(getTestReportPrefix() + getTestName(), error);
                }
            }
        }
        if (sendAggregated && errors.size() > 0)
        {
            for (BaseReporter reporter : reporters)
            {
                reporter.report(getTestReportPrefix() + getTestName() + " - " + errors.size() + " errors", aggregatedErrors.toString());
            }
        }
    }

    private Vector<String> baseRunTests()
    {
        try
        {
            runTests();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            exceptionToError(e);
        }
        return errors;
    }

    protected void exceptionToError(Exception e)
    {
        errors.add(getTestName() + " - Error happened while running test: " + e.toString());
    }


    private boolean shouldRun()
    {
        String alwaysRun = Manager.getProperty(Manager.ALWAYS_RUN);
        if (alwaysRun != null && alwaysRun.equals("true"))
        {
            System.out.println("Should run returning true (Config Override).");
            return true;
        }

        String historicValueName = getClass() + "_last_run";

        HistoricValue historicValue = HistoricValuesManager.getSaved(historicValueName);
        if (historicValue == null)
        {
            //System.out.println("First time. Returning true");
            markRunned(historicValueName);
            return true;
        }
        else
        {
            Date lastRunnedDate = historicValue.getTime();
            Date dateNow = new Date();

            long diff = DateHelper.getDateDiff(lastRunnedDate, dateNow, getRunEvery().getTimeUnit());
            if (diff >= getRunEvery().getCount())
            {
                //System.out.println("Diff is more than count. Returning true " + diff);
                markRunned(historicValueName);
                return true;
            }
            else
            {
                //System.out.println("Diff is less than count. Returning false " + diff);
                return false;
            }
        }
    }

    private void markRunned(String historicValueName)
    {
        HistoricValue historicValue = new HistoricValue(historicValueName);
        HistoricValuesManager.saveValue(historicValue, historicValueName);
    }

    public BaseTest addReporter(BaseReporter reporter)
    {
        reporters.add(reporter);
        return this;
    }


    /**
     * Runs tests and returns a Vector with error messages.
     *
     * @return
     */
    protected abstract Vector<String> runTests() throws Exception;

    protected boolean checkIfValueHasChanged(String variableName, String valueString, int timeUnitValue, TimeUnit timeUnit)
    {
        return checkIfValueHasChanged(variableName, valueString, timeUnitValue, timeUnit, false);
    }

    protected HistoricValue getStoredValue(String variableName)
    {
        return HistoricValuesManager.getSaved(variableName);
    }

    /**
     * Returns true if value has changed, false if not.
     *
     * @param variableName  - A variable name
     * @param valueString   - The actual value of the variable
     * @param timeUnitValue - How many timeUnits (24)
     * @param timeUnit      - TimeUnit (TimeUnit.HOURS, TimeUnit.MINUTES etc)
     * @param numeric       - Check if value has increased as numeric.
     * @return boolean - true if value has changed, false otherwise.
     */
    protected boolean checkIfValueHasChanged(String variableName, String valueString, int timeUnitValue, TimeUnit timeUnit, boolean numeric)
    {
        HistoricValue valueNow = new HistoricValue(valueString);

        HistoricValue value = HistoricValuesManager.getSaved(variableName);

        if (value == null)
        {
            System.out.println(variableName + " value does not exist. Creating now.");
            HistoricValuesManager.saveValue(valueNow, variableName);
            return true;
        }
        else
        {
            if (HistoricValuesManager.getDateDiff(value, valueNow, timeUnit) > timeUnitValue)
            {
                if (numeric)
                {
                    if (value.getValueAsDouble() >= valueNow.getValueAsDouble())
                    {
                        return false;
                    }
                    else
                    {
                        HistoricValuesManager.saveValue(valueNow, variableName);
                        return true;
                    }
                }
                else
                {
                    if (value.getValue().equals(valueNow.getValue()))
                    {
                        return false;
                    }
                    else
                    {
                        //todo not sure about line below
                        HistoricValuesManager.saveValue(valueNow, variableName);
                        return true;
                    }
                }
            }
            else
            {
                return true;
            }
        }
    }

    protected boolean checkIfNumericValueHasIncreased(String variableName, String subtitlesCountString, int timeUnitValue, TimeUnit timeUnit)
    {
        return checkIfValueHasChanged(variableName, subtitlesCountString, timeUnitValue, timeUnit, true);
    }

    protected void triggerErrorIfVariableHasntChanged(String value, String valueName, int idleHours, TimeUnit timeUnit)
    {
        try
        {
            Integer.parseInt(value);
            if (!checkIfNumericValueHasIncreased(valueName, value, idleHours, timeUnit))
            {
                errors.add(valueName + " variable hasn't change in at least " + idleHours + " hours.");
            }
        }
        catch (Exception e)
        {
            errors.add(valueName + " is not an integer.");
            e.printStackTrace();
        }
    }

    protected void triggerErrorIfVariableHasntChanged(String value, String valueName, int idleHours)
    {
        triggerErrorIfVariableHasntChanged(value, valueName, idleHours, TimeUnit.HOURS);
    }

    protected Vector<String> getDirectoryContents(SshConnectionData sshConnectionData, String dir) throws Exception
    {
        Vector<String> files = new Vector<String>();
        String command = "find " + dir;
        OsCommandOutput osCommandOutput = OsHelper.runRemoteCommand(sshConnectionData.getHost(), sshConnectionData.getPort(), command, sshConnectionData.getUser(), sshConnectionData.getDirectory(), sshConnectionData.getPrivateKey());
        if (osCommandOutput.getExitCode() != 0)
        {
            errors.add("getDirectoryContents error: " + command + ": " + osCommandOutput.getErrorOutput());
            return files;
        }
        else
        {
            String commandResult = osCommandOutput.getStandardOutput();
            Scanner sc = new Scanner(commandResult);
            while (sc.hasNext())
            {
                files.add(sc.nextLine());
            }
        }
        return files;
    }

    private String getTestReportPrefix()
    {
        return testReportPrefix;
    }

    protected void setTestReportPrefix(String prefix)
    {
        testReportPrefix = prefix;
    }

    public boolean isSendAggregated()
    {
        return sendAggregated;
    }

    public void setSendAggregated(boolean sendAggregated)
    {
        this.sendAggregated = sendAggregated;
    }
}
