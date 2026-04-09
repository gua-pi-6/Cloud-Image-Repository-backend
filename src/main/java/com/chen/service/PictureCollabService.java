package com.chen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chen.model.dto.picture.collab.PictureCollabApplyRequest;
import com.chen.model.dto.picture.collab.PictureCollabApplyResult;
import com.chen.model.dto.picture.collab.PictureCollabStateVo;
import com.chen.model.entity.PictureCollabState;

/**
 * 图片协同编辑核心服务。
 *
 * <p>协议层只负责收发消息，真正的状态读取、操作应用、幂等去重都收敛到这里。
 */
public interface PictureCollabService extends IService<PictureCollabState> {

    /**
     * 读取当前图片协同快照；如果尚未初始化，则自动创建初始状态。
     */
    PictureCollabStateVo getOrInitState(Long pictureId);

    /**
     * 应用一次客户端操作，并返回服务端确认后的最新结果。
     */
    PictureCollabApplyResult applyOperation(PictureCollabApplyRequest applyRequest);
}
