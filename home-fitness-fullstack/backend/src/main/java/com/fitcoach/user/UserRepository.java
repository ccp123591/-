package com.fitcoach.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findByOpenId(String openId);
    Optional<User> findByDeviceId(String deviceId);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);

    /** 管理后台关键字搜索（邮箱/昵称模糊匹配），keyword 为 null 时返回全部。 */
    @Query("""
        select u from User u
        where :kw is null
           or lower(u.email) like lower(concat('%', :kw, '%'))
           or lower(u.nickname) like lower(concat('%', :kw, '%'))
        """)
    Page<User> search(@Param("kw") String keyword, Pageable pageable);
}
