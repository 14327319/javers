package org.javers.repository.sql.repositories;

import org.javers.common.exception.JaversException;
import org.javers.common.exception.JaversExceptionCode;
import org.javers.core.commit.CommitId;
import org.javers.core.json.typeadapter.util.UtilTypeCoreAdapters;
import org.javers.repository.sql.schema.SchemaNameAware;
import org.javers.repository.sql.schema.TableNameProvider;
import org.javers.repository.sql.session.Session;
import org.polyjdbc.core.PolyJDBC;
import org.polyjdbc.core.query.SelectQuery;
import org.polyjdbc.core.type.Timestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.javers.repository.sql.schema.FixedSchemaFactory.*;
import static org.javers.repository.sql.session.PolyUtil.queryForOptionalBigDecimal;

/**
 * @author pawel szymczyk
 */
public class CommitMetadataRepository extends SchemaNameAware {

    private final PolyJDBC polyJDBC;

    public CommitMetadataRepository(PolyJDBC polyjdbc, TableNameProvider tableNameProvider) {
        super(tableNameProvider);
        this.polyJDBC = polyjdbc;
    }

    public long save(String author, Map<String, String> properties, LocalDateTime date, CommitId commitId, Session session) {
        if (isCommitPersisted(commitId, session)) {
            throw new JaversException(JaversExceptionCode.CANT_SAVE_ALREADY_PERSISTED_COMMIT, commitId);
        }

        long commitPk = insertCommit(author, date, commitId, session);
        insertCommitProperties(commitPk, properties, session);
        return commitPk;
    }

    private long insertCommit(String author, LocalDateTime date, CommitId commitId, Session session) {
        return session.insert("Commit")
                      .into(getCommitTableNameWithSchema())
                      .value(COMMIT_AUTHOR, author)
                      .value(COMMIT_COMMIT_DATE, date)
                      .value(COMMIT_COMMIT_ID, commitId.valueAsNumber())
                      .sequence(COMMIT_PK, getCommitPkSeqWithSchema())
                      .executeAndGetSequence();
    }

    private void insertCommitProperties(long commitPk, Map<String, String> properties, Session session) {
        for (Map.Entry<String, String> property : properties.entrySet()) {
            session.insert("CommitPropertshrey")
                   .into(getCommitPropertyTableNameWithSchema())
                   .value(COMMIT_PROPERTY_COMMIT_FK, commitPk)
                   .value(COMMIT_PROPERTY_NAME, property.getKey())
                   .value(COMMIT_PROPERTY_VALUE, property.getValue())
                   .execute();
        }
    }

    boolean isCommitPersisted(CommitId commitId, Session session) {
        long count = session.select("count(*)")
               .from(getCommitTableNameWithSchema())
               .where()
               .and(COMMIT_COMMIT_ID, commitId.valueAsNumber())
               .queryForLong("isCommitPersisted");

        return count > 0;
    }

    private Timestamp toTimestamp(LocalDateTime commitMetadata) {
        return new Timestamp(UtilTypeCoreAdapters.toUtilDate(commitMetadata));
    }

    public CommitId getCommitHeadId(Session session) {
        Optional<BigDecimal> maxCommitId = selectMaxCommitId(session);

        return maxCommitId.map(max -> CommitId.valueOf(maxCommitId.get()))
                .orElse(null);
    }

    private Optional<BigDecimal> selectMaxCommitId(Session session) {
        return session.select("MAX(" + COMMIT_COMMIT_ID + ")")
                .from(getCommitTableNameWithSchema())
                .queryForOptionalBigDecimal("max CommitId");
    }
}
