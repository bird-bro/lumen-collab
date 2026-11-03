package org.birdbro.lumen.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.birdbro.common.core.base.EntityBase;

/**
 * Platform user ({@code user_info}, auth-rbac-foundation task 2.3). {@code username} is
 * unique among live rows (partial unique index - a logically deleted row does not block
 * re-creating the username; the in-service duplicate rejection remains the A-class path).
 * {@code password} holds the BCrypt hash (design D4), never the plain text.
 *
 * @author birdbro
 */
@TableName("user_info")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserInfo extends EntityBase {

    /** Primary key, database auto-increment (design D1). */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** Login name, unique among live rows. */
    private String username;

    /** BCrypt password hash (design D4) - never the plain text. */
    private String password;

    /** 1 = the user must change the password at first login (seeded admin). */
    private Integer mustChangePassword;

    /** Enable flag: 1 = active (default), 0 = disabled. */
    private Integer enable;

    /** Display name. */
    private String name;
}
