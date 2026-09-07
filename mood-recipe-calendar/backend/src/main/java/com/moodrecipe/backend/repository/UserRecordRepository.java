package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.UserRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRecordRepository extends JpaRepository<UserRecord, Long> {

    /** 某用户全部记录，按时间倒序 */
    List<UserRecord> findByOpenidOrderByCreatedAtDesc(String openid);

    /** 个性化寄语只需观察最近记录，避免用户数据增长后全表读取。 */
    List<UserRecord> findTop30ByOpenidOrderByCreatedAtDesc(String openid);

    /** 某用户某月（YYYY-MM）的记录 */
    List<UserRecord> findByOpenidAndRecordDateStartingWith(String openid, String month);

    /** 某用户某天的记录 */
    List<UserRecord> findByOpenidAndRecordDate(String openid, String date);

    long countByOpenid(String openid);
}
