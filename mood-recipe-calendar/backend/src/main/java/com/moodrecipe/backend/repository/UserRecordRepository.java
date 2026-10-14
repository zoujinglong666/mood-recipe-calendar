package com.moodrecipe.backend.repository;

import com.moodrecipe.backend.entity.UserRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

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

    /** 快手菜成就：cookingTime 为 null 的记录不计入。 */
    long countByOpenidAndCookingTimeLessThanEqual(String openid, Integer cookingTime);

    /** 点评成就：写了备注（note 非空）的记录数。 */
    long countByOpenidAndNoteIsNotEmpty(String openid);

    Optional<UserRecord> findByOpenidAndClientRequestId(String openid, String clientRequestId);
}
