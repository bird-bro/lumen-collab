package org.birdbro.common.core;


import lombok.Data;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 统一返回
 *
 * @author bird
 * @date 2021-7-21 8:47
 **/
@Data
public class R<T> implements Serializable {
    /**
     * 服务器当前时间戳
     */
    private String timestamp = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS").format(new Date());

    /**
     * 错误码
     */
    private String code;

    /**
     * 错误描述
     */
    private String msg;

    /**
     * 返回数据
     */
    private T data;


    public R() {
    }

    public R(String timestamp, T data, String code, String msg) {
        this.timestamp = timestamp;
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 200 处理成功
     **/
    public static <T> R<T> ofSuccess(T data) {
        R<T> result = new R<>();
        result.code = String.valueOf(HttpStatus.OK.value());
        result.msg = HttpStatus.OK.getReasonPhrase();
        result.setData(data);
        return result;
    }
    

    /**
     * 204 请求已经执行成功，但没有内容
     **/
    public static R<?> ofSuccess() {
        R result = new R();

        result.code = String.valueOf(HttpStatus.NO_CONTENT.value());
        result.msg = HttpStatus.NO_CONTENT.getReasonPhrase();
        return result;
    }

    /**
     * 请求错误
     **/
    public static R ofFail(String code, String msg) {
        R result = new R();
        result.code = code;
        result.msg = msg;
        return result;
    }

    public static R<Void> ofFail(String code, String msg, Object data) {
        R<Void> result = new R<>();
        result.code = code;
        result.msg = msg;
        return result;
    }

    @Override
    public String toString() {
        return "Result{" +
                "timestamp=" + timestamp + '\'' +
                ", code='" + code +
                ", msg='" + msg +
                ", data=" + data + '\'' +
                '}';
    }
}
