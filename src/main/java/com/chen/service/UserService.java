package com.chen.service;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chen.model.dto.picture.PictureQueryRequest;
import com.chen.model.dto.user.UserQueryRequest;
import com.chen.model.entity.Picture;
import com.chen.model.entity.User;
import com.chen.model.vo.LoginUserVO;
import com.chen.model.vo.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 用户(User)表服务接口
 *
 * <p>协同编辑只依赖这里的一个能力：判断当前登录用户是否为管理员。
 *
 * @author makejava
 * @since 2025-12-28 21:49:56
 */
public interface UserService extends IService<User> {

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);


    /**
     * 用户注销
     *
     * @param request 请求
     * @return 是否注销成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);
    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request       请求
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取加密密码
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取当前登录用户
     *
     * @param request 请求
     * @return 当前登录用户
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return 当前登录用户信息
     */
    LoginUserVO getLoginUserVO(User user);


    /**
     * 获取脱敏后的用户信息
     *
     * @param user    用户
     * @return 脱敏后的用户信息
     */
    UserVO getUserVO(User user);


    /**
     * 获取脱敏后的用户信息列表 (给管理员使用)
     *
     * @param userList 用户列表
     * @return 脱敏后的用户信息列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取查询条件
     *
     * @param userQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);


}
