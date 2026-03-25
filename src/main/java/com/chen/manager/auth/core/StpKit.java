package com.chen.manager.auth.core;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;


/**
 * StpLogic 门面类，管理项目中所有的 StpLogic 账号体系
 */

public class StpKit {

    /**
     * 默认原生会话对象
     */
    public static final StpLogic DEFAULT = StpUtil.stpLogic;

    public static final String SPACE_TYPE= "space";

    /**
     * space 会话对象 (区分公共图库,私人空间和团队空间,并对团队空间中的成员进行权限管理)
     */
    public static final StpLogic SPACE = new StpLogic(SPACE_TYPE);

}
