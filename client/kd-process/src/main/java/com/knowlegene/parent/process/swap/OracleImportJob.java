package com.knowlegene.parent.process.swap;

import com.knowlegene.parent.config.common.constantenum.DBOperationEnum;
import com.knowlegene.parent.config.common.event.OracleImportType;
import com.knowlegene.parent.config.util.BaseUtil;
import com.knowlegene.parent.config.util.JdbcUtil;
import com.knowlegene.parent.process.pojo.db.DBOptions;
import com.knowlegene.parent.process.pojo.SwapOptions;
import com.knowlegene.parent.process.swap.event.OracleImportTaskEvent;
import com.knowlegene.parent.scheduler.event.EventHandler;
import com.knowlegene.parent.scheduler.utils.CacheManager;
import org.apache.beam.sdk.schemas.Schema;
import org.apache.beam.sdk.schemas.SchemaCoder;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.Row;


/**
 * @Author: limeng
 * @Date: 2019/8/20 16:44
 */
public class OracleImportJob extends ImportJobBase{
    private volatile static DBOptions dbOptions = null;

    public OracleImportJob() {
    }

    public OracleImportJob(SwapOptions opts) {
        super(opts);
    }



    private static DBOptions getDbOptions(){
        if(dbOptions == null){
            String name = DBOperationEnum.ORACLE_IMPORT.getName();
            Object options = getOptions(name);
            if(options != null){
                dbOptions = (DBOptions)options;
            }
        }
        return dbOptions;
    }

    /**
     * 保存
     * @param rows
     * @param schema
     * @param tableName
     * @return
     */
    private static void saveBySQL(PCollection<Row> rows, Schema schema, String tableName){
        if(rows !=null && schema!=null){
            String insertSQL = getInsertSQL(schema, tableName);
            getLogger().info("insertSQL:{}",insertSQL);
            if(BaseUtil.isNotBlank(insertSQL)){
                rows.apply(getOracleSwapImport().saveByIO(insertSQL));
            }
        }
    }

    /**
     * 批量保存sql
     * @param schema
     * @param tableName
     * @return
     */
    private static String getInsertSQL(Schema schema,String tableName){
        String result="";
        if(schema!=null && BaseUtil.isNotBlank(tableName)){
            result = JdbcUtil.getInsertSQL(schema,tableName);
        }
        return result;
    }


    private static Schema getOracleSchemas(){
        String[] dbColumn = getDbOptions().getDbColumn();
        String tableName = getDbOptions().getTableName();
        //getDbOptions()
        Schema allSchema = getOracleSwapImport().desc(tableName);

        getLogger().info("oracle=>tableName:{}",tableName);
        return JdbcUtil.columnConversion(dbColumn, allSchema);


    }


    public static void save(PCollection<Row> rows) {
        if(rows != null){
            String tableName = getDbOptions().getTableName();
            Schema schema = getOracleSchemas();
            if(schema == null){
                getLogger().info("schema is null");
            }
            PCollection<Row> newRows = rows.setCoder(SchemaCoder.of(schema));
            saveBySQL(newRows,schema,tableName);
        }
    }


    public static class OracleImportDispatcher implements EventHandler<OracleImportTaskEvent> {
        @Override
        public void handle(OracleImportTaskEvent event) {
            if(event.getType() == OracleImportType.T_IMPORT){
                getLogger().info("OracleImportDispatcher is start");

                if(CacheManager.isExist(DBOperationEnum.PCOLLECTION_QUERYS.getName())){
                    PCollection<Row>  rows = (PCollection<Row>)CacheManager.getCache(DBOperationEnum.PCOLLECTION_QUERYS.getName());
                    save(rows);
                }

            }
        }
    }



}
