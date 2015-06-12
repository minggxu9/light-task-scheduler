package com.lts.biz.logger.mysql;

import com.alibaba.fastjson.TypeReference;
import com.lts.biz.logger.JobLogException;
import com.lts.biz.logger.JobLogger;
import com.lts.biz.logger.domain.JobLoggerRequest;
import com.lts.biz.logger.domain.LogType;
import com.lts.core.cluster.Config;
import com.lts.core.commons.file.FileUtils;
import com.lts.core.commons.utils.CollectionUtils;
import com.lts.core.commons.utils.JSONUtils;
import com.lts.core.constant.Level;
import com.lts.core.domain.PageResponse;
import com.lts.biz.logger.domain.JobLogPo;
import com.lts.store.jdbc.JdbcRepository;
import com.lts.store.jdbc.SqlBuilder;
import org.apache.commons.dbutils.ResultSetHandler;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author Robert HG (254963746@qq.com) on 5/21/15.
 */
public class MysqlJobLogger extends JdbcRepository implements JobLogger {

    private String insertSQL;

    public MysqlJobLogger(Config config) {
        super(config);
        doCreateTable();

        insertSQL = "INSERT INTO `lts_job_log_po` (`timestamp`, `log_type`, `success`, `msg`" +
                ",`task_tracker_identity`, `level`, `task_id`, `job_id`" +
                ", `priority`, `submit_node_group`, `task_tracker_node_group`, `ext_params`, `need_feedback`" +
                ", `cron_expression`, `trigger_time`)" +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    public void log(JobLogPo jobLogPo) {
        if (jobLogPo == null) {
            return;
        }
        try {
            getSqlTemplate().update(insertSQL,
                    jobLogPo.getTimestamp(),
                    jobLogPo.getLogType().name(),
                    jobLogPo.isSuccess(),
                    jobLogPo.getMsg(),
                    jobLogPo.getTaskTrackerIdentity(),
                    jobLogPo.getLevel().name(),
                    jobLogPo.getTaskId(),
                    jobLogPo.getJobId(),
                    jobLogPo.getPriority(),
                    jobLogPo.getSubmitNodeGroup(),
                    jobLogPo.getTaskTrackerNodeGroup(),
                    JSONUtils.toJSONString(jobLogPo.getExtParams()),
                    jobLogPo.isNeedFeedback(),
                    jobLogPo.getCronExpression(),
                    jobLogPo.getTriggerTime()
            );
        } catch (SQLException e) {
            throw new JobLogException(e.getMessage(), e);
        }
    }

    @Override
    public void log(List<JobLogPo> jobLogPos) {
        if (CollectionUtils.isEmpty(jobLogPos)) {
            return;
        }
        String prefixSQL = "INSERT INTO `lts_job_log_po` (`timestamp`, `log_type`, `success`, `msg`" +
                ",`task_tracker_identity`, `level`, `task_id`, `job_id`" +
                ", `priority`, `submit_node_group`, `task_tracker_node_group`, `ext_params`, `need_feedback`" +
                ", `cron_expression`, `trigger_time`) VALUES ";
        int size = jobLogPos.size();
        for (int i = 0; i < size; i++) {
            if (i == size - 1) {
                prefixSQL += "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            } else {
                prefixSQL += "(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?),";
            }
        }

        Object[][] params = new Object[size][15];
        int index = 0;
        for (JobLogPo jobLogPo : jobLogPos) {
            int i = index++;
            params[i][0] = jobLogPo.getTimestamp();
            params[i][1] = jobLogPo.getLogType().name();
            params[i][2] = jobLogPo.isSuccess();
            params[i][3] = jobLogPo.getMsg();
            params[i][4] = jobLogPo.getTaskTrackerIdentity();
            params[i][5] = jobLogPo.getLevel().name();
            params[i][6] = jobLogPo.getTaskId();
            params[i][7] = jobLogPo.getJobId();
            params[i][8] = jobLogPo.getPriority();
            params[i][9] = jobLogPo.getSubmitNodeGroup();
            params[i][10] = jobLogPo.getTaskTrackerNodeGroup();
            params[i][11] = JSONUtils.toJSONString(jobLogPo.getExtParams());
            params[i][12] = jobLogPo.isNeedFeedback();
            params[i][13] = jobLogPo.getCronExpression();
            params[i][14] = jobLogPo.getTriggerTime();
        }

        try {
            getSqlTemplate().batchUpdate(prefixSQL, params);
        } catch (SQLException e) {
            throw new JobLogException(e);
        }
    }

    private ResultSetHandler<List<JobLogPo>> RESULT_SET_HANDLER = new ResultSetHandler<List<JobLogPo>>() {
        @Override
        public List<JobLogPo> handle(ResultSet rs) throws SQLException {
            List<JobLogPo> result = new ArrayList<JobLogPo>();
            while (rs.next()) {
                JobLogPo jobLogPo = new JobLogPo();
                jobLogPo.setTimestamp(rs.getLong("timestamp"));
                jobLogPo.setLogType(LogType.valueOf(rs.getString("log_type")));
                jobLogPo.setSuccess(rs.getBoolean("success"));
                jobLogPo.setMsg(rs.getString("msg"));
                jobLogPo.setTaskTrackerIdentity(rs.getString("task_tracker_identity"));
                jobLogPo.setLevel(Level.valueOf(rs.getString("level")));
                jobLogPo.setTaskId(rs.getString("task_id"));
                jobLogPo.setJobId(rs.getString("job_id"));
                jobLogPo.setPriority(rs.getInt("priority"));
                jobLogPo.setSubmitNodeGroup(rs.getString("submit_node_group"));
                jobLogPo.setTaskTrackerNodeGroup(rs.getString("task_tracker_node_group"));
                jobLogPo.setExtParams(JSONUtils.parse(rs.getString("ext_params"), new TypeReference<Map<String, String>>() {
                }));
                jobLogPo.setNeedFeedback(rs.getBoolean("need_feedback"));
                jobLogPo.setCronExpression(rs.getString("cron_expression"));
                jobLogPo.setTriggerTime(rs.getLong("trigger_time"));
                result.add(jobLogPo);
            }
            return result;
        }
    };


    private Long getTimestamp(Date timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.getTime();
    }

    @Override
    public PageResponse<JobLogPo> search(JobLoggerRequest request) {

        PageResponse<JobLogPo> response = new PageResponse<JobLogPo>();

        try {
            // 查询count
            SqlBuilder countSQL = new SqlBuilder("SELECT count(1) FROM `lts_job_log_po` ");
            countSQL.addCondition("task_id", request.getTaskId());
            countSQL.addCondition("task_tracker_node_group", request.getTaskTrackerNodeGroup());
            countSQL.addCondition("timestamp", getTimestamp(request.getStartTimestamp()), ">=");
            countSQL.addCondition("timestamp", getTimestamp(request.getEndTimestamp()), "<=");
            Long results = getSqlTemplate().queryForValue(countSQL.getSQL(), countSQL.getParams().toArray());
            response.setResults(results.intValue());
            if (results == 0) {
                return response;
            }
            // 查询 rows
            SqlBuilder rowsSQL = new SqlBuilder("SELECT * FROM `lts_job_log_po` ");
            rowsSQL.addCondition("task_id", request.getTaskId());
            rowsSQL.addCondition("task_tracker_node_group", request.getTaskTrackerNodeGroup());
            rowsSQL.addCondition("timestamp", getTimestamp(request.getStartTimestamp()), ">=");
            rowsSQL.addCondition("timestamp", getTimestamp(request.getEndTimestamp()), "<=");

            rowsSQL.addOrderBy("timestamp", "DESC");
            rowsSQL.addLimit(request.getStart(), request.getLimit());
            List<JobLogPo> rows = getSqlTemplate().query(rowsSQL.getSQL(), RESULT_SET_HANDLER, rowsSQL.getParams().toArray());
            response.setRows(rows);
        } catch (SQLException e) {
            throw new JobLogException(e);
        }
        return response;
    }

    private void doCreateTable() {
        // 创建表
        try {
            InputStream is = this.getClass().getClassLoader().getResourceAsStream("sql/lts_job_log_po.sql");
            getSqlTemplate().update(FileUtils.read(is));
        } catch (SQLException e) {
            throw new JobLogException("create table error!", e);
        } catch (IOException e) {
            throw new JobLogException("create table error!", e);
        }
    }
}