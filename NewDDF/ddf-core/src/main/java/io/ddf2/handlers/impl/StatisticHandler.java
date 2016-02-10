package io.ddf2.handlers.impl;

import io.ddf2.DDFException;
import io.ddf2.IDDF;
import io.ddf2.ISqlResult;
import io.ddf2.analytics.CategoricalSimpleSummary;
import io.ddf2.analytics.FiveNumSummary;
import io.ddf2.analytics.NumericSimpleSummary;
import io.ddf2.analytics.SimpleSummary;
import io.ddf2.datasource.schema.IColumn;
import io.ddf2.handlers.IStatisticHandler;
import org.apache.commons.lang.StringUtils;

import java.util.*;

public abstract class StatisticHandler implements IStatisticHandler {
    protected IDDF ddf;

    public StatisticHandler(IDDF ddf) {
        this.ddf = ddf;
    }

    // TODO (sang): Why these 2 functions are put in statisticHandler? I think it's for column types and should be in IColumn?
    protected abstract boolean isIntegral(Class type);

    protected abstract boolean isFractional(Class type);

    @Override
    public SimpleSummary[] getSimpleSummary() throws DDFException {
        List<IColumn> categoricalColumns = this.getCategoricalColumns();
        List<SimpleSummary> simpleSummaries = new ArrayList<>();
        for (IColumn column: categoricalColumns) {
            String sqlcmd = String.format("SELECT DISTINCT(%s) FROM %s WHERE %s IS NOT NULL",
                    column.getName(),
                    this.getDDF().getDDFName(),
                    column.getName());
            ISqlResult sqlResult = this.getDDF().sql(sqlcmd);
            List<String> values = new ArrayList<>();
            while (sqlResult.next()) {
                values.add(sqlResult.get(0).toString());
            }
            CategoricalSimpleSummary summary = new CategoricalSimpleSummary();
            summary.setValues(values);
            summary.setColumnName(column.getName());
            simpleSummaries.add(summary);
        }

        List<IColumn> numericCols = this.getNumericColumns();
        List<String> selectFields = new ArrayList<>();
        for (IColumn column: numericCols) {
            selectFields.add(String.format("min(%s), max(%s)", column.getName(), column.getName()));
        }

        String sqlcmd = String.format("SELECT %s FROM %s", StringUtils.join(selectFields, ", "),
            this.getDDF().getDDFName());
        int index = 0;
        ISqlResult sqlResult = this.getDDF().sql(sqlcmd);
        if (sqlResult.next()) {
            for (IColumn column: numericCols) {
                NumericSimpleSummary summary = new NumericSimpleSummary();
                summary.setColumnName(column.getName());
                Double dbl = sqlResult.getDouble(index);
                summary.setMin(dbl == null ? Double.NaN : dbl);
                dbl = sqlResult.getDouble(index + 1);
                summary.setMax(dbl == null ? Double.NaN : dbl);
                simpleSummaries.add(summary);
                // Skip to next column.
                index += 2;
            }
        }
        return simpleSummaries.toArray(new SimpleSummary[simpleSummaries.size()]);
    }

    protected abstract String getFiveNumQuery(String columnName);

    @Override
    public FiveNumSummary[] getFiveNumSummary(List<String> columnNames) throws DDFException {
        // This use sql, should be fine.
        assert columnNames != null && columnNames.size() > 0;
        FiveNumSummary[] fiveNumSummaries = new FiveNumSummary[columnNames.size()];
        List<String> numericCols = new ArrayList<String>();
        for (String columnName  : columnNames) {
            if (this.getDDF().getSchema().getColumn(columnName).isNumeric()) {
                numericCols.add(columnName);
            }
        }
        String[] rs = null;
        if (numericCols.size() > 0) {
            List<String> queries = new ArrayList<String>();
            for (String columnName : columnNames) {
                String query = getFiveNumQuery(columnName);
                if (query != null && query.length() > 0) {
                    queries.add(query);
                }
            }
            String sqlcmd = String.format("SELECT %s FROM %%s", StringUtils.join(queries.toArray(new String[0]), ", "));
            // a fivenumsummary of an Int/Long column is in the format "[min, max, 1st_quantile, median, 3rd_quantile]"
            // each value can be a NULL
            // a fivenumsummary of an Double/Float column is in the format "min \t max \t[1st_quantile, median, 3rd_quantile]"
            // or "min \t max \t null"s
            ISqlResult sqlResult = this.getDDF().sql(sqlcmd);
            // What's here.
            int k = 0;
            for (int i = 0; i < columnNames.size(); ++i) {
                if (!this.getDDF().getSchema().getColumn(columnNames.get(i)).isNumeric()) {
                    fiveNumSummaries[i] = new FiveNumSummary(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
                } else {
                    fiveNumSummaries[i] = new FiveNumSummary(sqlResult.getDouble(5 * k),
                        sqlResult.getDouble(5 * k + 1),
                        sqlResult.getDouble(5 * k + 2),
                        sqlResult.getDouble(5 * k + 3),
                        sqlResult.getDouble(5 * k + 4));
                    ++k;
                }
            }
        }
        return fiveNumSummaries;
    }


    @Override
    public Double[] getQuantiles(String columnName, Double[] percentiles) throws DDFException {
        assert columnName != null;
        assert percentiles.length > 0;
        Set<Double> setPercentiles = new HashSet(Arrays.asList(percentiles));
        boolean hasZero = setPercentiles.contains(0.0);
        boolean hasOne = setPercentiles.contains(1.0);
        setPercentiles.remove(0.0);
        setPercentiles.remove(1.0);
        List<Double> listPercentiles = new ArrayList(setPercentiles);
        List<String> sqlSelect = new ArrayList<String>();


        if (listPercentiles.size() > 0) {
            IColumn column = ddf.getSchema().getColumn(columnName);
            if (column.isIntegral()) {
                sqlSelect.add(String.format("percentile(%s,array(%s))", columnName, StringUtils.join(listPercentiles, ",")));
            } else if (column.isFractional()) {
                sqlSelect.add(String.format("percentile_approx(%s,array(%s))", columnName, StringUtils.join(listPercentiles, ",")));
            } else {
                throw new DDFException("Only support numeric vectors!!!");
            }
        }
        if (hasZero) sqlSelect.add("min(" + columnName + ")");
        if (hasOne) sqlSelect.add("max(" + columnName + ")");


        String sqlCmd = String.format("SELECT %s FROM %s", StringUtils.join(sqlSelect, ","), ddf.getDDFName());

        ISqlResult sqlResult = ddf.sql(sqlCmd);

        //Todo: @Jing plz help to parse the result. I don't know which kind we want to parse here.
        return new Double[0];
    }

    @Override
    public Double[] getVariance(String columnName) throws DDFException {
        String sqlCmd = String.format("select var_samp(%s) from %s", columnName, ddf.getDDFName());
        ISqlResult result = ddf.sql(sqlCmd);
        if (result.next()) {
            Double tmp = result.getDouble(0);
            Double[] variance = new Double[2];
            variance[0] = tmp;
            variance[1] = Math.sqrt(tmp);
            return variance;
        } else {
            throw new DDFException("Unable to get sql result cmd: " + sqlCmd);
        }
    }

    @Override
    public Double getMean(String columnName) throws DDFException {
        String sqlCmd = String.format("select avg(%s) from %s", columnName, ddf.getDDFName());
        ISqlResult sqlResult = ddf.sql(sqlCmd);
        if (sqlResult.next()) {
            return sqlResult.getDouble(0);
        } else {
            throw new DDFException("Unable to get sql result cmd: " + sqlCmd);
        }
    }

    @Override
    public Double getCor(String xColumnName, String yColumnName) throws DDFException {
        String sqlCmd = String.format("select corr(%s, %s) from %s", xColumnName, yColumnName, ddf.getDDFName());
        ISqlResult sqlResult = ddf.sql(sqlCmd);
        if (sqlResult.next()) {
            return sqlResult.getDouble(0);
        } else {
            throw new DDFException("Unable to get sql result cmd: " + sqlCmd);
        }
    }

    @Override
    public Double getCovariance(String xColumnName, String yColumnName) throws DDFException {
        String sqlCmd = String.format("select covar_samp(%s, %s) from %s", xColumnName, yColumnName, ddf.getDDFName());
        ISqlResult sqlResult = ddf.sql(sqlCmd);
        if (sqlResult.next()) {
            return sqlResult.getDouble(0);
        } else {
            throw new DDFException("Unable to get sql result cmd: " + sqlCmd);
        }
    }

    @Override
    public Double getMin(String columnName) throws DDFException {
        String sqlCmd = String.format("select min(%s) from %s", columnName, ddf.getDDFName());
        ISqlResult sqlResult = ddf.sql(sqlCmd);
        if (sqlResult.next()) {
            return sqlResult.getDouble(0);
        } else {
            throw new DDFException("Unable to get sql result cmd: " + sqlCmd);
        }
    }

    @Override
    public Double getMax(String columnName) throws DDFException {
        String sqlCmd = String.format("select max(%s) from %s", columnName, ddf.getDDFName());
        ISqlResult sqlResult = ddf.sql(sqlCmd);
        if (sqlResult.next()) {
            return sqlResult.getDouble(0);
        } else {
            throw new DDFException("Unable to get sql result cmd: " + sqlCmd);
        }
    }

    @Override
    public IDDF getDDF() {
        return ddf;
    }

    private List<IColumn> getCategoricalColumns() {
        List<IColumn> columns = new ArrayList<IColumn>();
        for(IColumn column: this.getDDF().getSchema().getColumns()) {
            // TODO: how to check factor
            if(column.getFactor() != null) {
                columns.add(column);
            }
        }
        return columns;
    }

    private List<IColumn> getNumericColumns() {
        List<IColumn> columns = new ArrayList<IColumn>();
        for(IColumn column: this.getDDF().getSchema().getColumns()) {
           if (column.isNumeric()) {
               columns.add(column);
           }
        }
        return columns;
    }
}
