package com.chen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chen.constant.PictureCollabConstant;
import com.chen.exception.BusinessException;
import com.chen.exception.ErrorCode;
import com.chen.exception.ThrowUtils;
import com.chen.mapper.PictureCollabOpLogMapper;
import com.chen.mapper.PictureCollabStateMapper;
import com.chen.model.dto.picture.collab.PictureCollabApplyRequest;
import com.chen.model.dto.picture.collab.PictureCollabApplyResult;
import com.chen.model.dto.picture.collab.PictureCollabStateVo;
import com.chen.model.entity.Picture;
import com.chen.model.entity.PictureCollabOpLog;
import com.chen.model.entity.PictureCollabState;
import com.chen.service.PictureCollabService;
import com.chen.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
/**
 * 图片协同编辑核心实现。
 *
 * <p>这套实现不是“字符级文本 OT”，而是更适合固定状态对象的 OT-lite：
 * - 旋转：相对增量
 * - 缩放：相对因子
 * - 裁剪框：整块覆盖
 *
 * <p>核心思想是：
 * 1. 客户端带 baseRevision 提交操作；
 * 2. 服务端总是在“当前最新状态”上串行应用；
 * 3. 用 opId 做幂等去重；
 * 4. 用 revision 表示权威状态推进。
 */
public class PictureCollabServiceImpl extends ServiceImpl<PictureCollabStateMapper, PictureCollabState> implements PictureCollabService {

    private static final double DEFAULT_ANGLE = 0D;

    private static final double DEFAULT_SCALE = 1D;

    private static final double MIN_SCALE = 0.1D;

    private static final double MAX_SCALE = 10D;

    private static final double MIN_CROP_SIZE = 1D;

    /**
     * 按图片维度加本地锁，保证单 JVM 内同一张图的操作严格串行化。
     */
    private final ConcurrentHashMap<Long, ReentrantLock> pictureLockMap = new ConcurrentHashMap<>();

    @Resource
    private PictureCollabStateMapper pictureCollabStateMapper;

    @Resource
    private PictureCollabOpLogMapper pictureCollabOpLogMapper;

    @Resource
    private PictureService pictureService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public PictureCollabStateVo getOrInitState(Long pictureId) {
        ThrowUtils.throwIf(ObjUtil.isNull(pictureId) || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        checkPictureExists(pictureId);

        // 初始化快照也要串行化，避免并发首连时重复插入。
        ReentrantLock lock = pictureLockMap.computeIfAbsent(pictureId, key -> new ReentrantLock());
        lock.lock();
        try {
            PictureCollabState state = getOrCreateState(pictureId);
            return toStateDTO(state);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public PictureCollabApplyResult applyOperation(PictureCollabApplyRequest applyRequest) {
        validateApplyRequest(applyRequest);

        // 锁负责解决并发顺序问题；事务负责保证“更新快照 + 写日志”原子提交。
        ReentrantLock lock = pictureLockMap.computeIfAbsent(applyRequest.getPictureId(), key -> new ReentrantLock());
        lock.lock();
        try {
            PictureCollabApplyResult result = transactionTemplate.execute(status -> doApplyOperation(applyRequest));
            ThrowUtils.throwIf(result == null, ErrorCode.OPERATION_ERROR);
            return result;
        } finally {
            lock.unlock();
        }
    }

    private PictureCollabApplyResult doApplyOperation(PictureCollabApplyRequest applyRequest) {
        // 第一步先查 opId，命中则说明客户端重试或网络重复投递，直接回放结果即可。
        PictureCollabOpLog existingOpLog = getOpLogByOpId(applyRequest.getOpId());
        if (existingOpLog != null) {
            PictureCollabApplyResult result = toApplyResult(existingOpLog);
            result.setDuplicated(true);
            return result;
        }

        // 再读取当前最新快照。后续所有运算都以服务端最新状态为准。
        PictureCollabState state = getOrCreateState(applyRequest.getPictureId());
        Long currentRevision = ObjUtil.defaultIfNull(state.getRevision(), 0L);
        if (applyRequest.getBaseRevision() > currentRevision) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "baseRevision cannot exceed current revision");
        }

        double nextAngle = ObjUtil.defaultIfNull(state.getAngle(), DEFAULT_ANGLE);
        double nextScale = ObjUtil.defaultIfNull(state.getScale(), DEFAULT_SCALE);
        Double nextCropX = state.getCropX();
        Double nextCropY = state.getCropY();
        Double nextCropWidth = state.getCropWidth();
        Double nextCropHeight = state.getCropHeight();
        String opType = applyRequest.getOpType();
        Double opValue = applyRequest.getValue();

        // 这里是协同算法的核心：
        // 旋转/缩放属于“相对操作”，所以天然适合重基线后继续应用；
        // 裁剪框属于“绝对覆盖”，谁后到服务端就覆盖成谁的矩形。
        if (PictureCollabConstant.OP_ROTATE_DELTA.equals(opType)) {
            nextAngle = normalizeAngle(nextAngle + opValue);
        } else if (PictureCollabConstant.OP_SCALE_FACTOR.equals(opType)) {
            nextScale = clampScale(nextScale * opValue);
        } else if (PictureCollabConstant.OP_CROP_BOX.equals(opType)) {
            nextCropX = applyRequest.getCropX();
            nextCropY = applyRequest.getCropY();
            nextCropWidth = applyRequest.getCropWidth();
            nextCropHeight = applyRequest.getCropHeight();
            opValue = 0D;
        } else {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "unsupported opType");
        }

        // 服务端版本号只在真正成功应用一次操作后前进一格。
        long serverRevision = currentRevision + 1;
        state.setRevision(serverRevision);
        state.setAngle(nextAngle);
        state.setScale(nextScale);
        state.setCropX(nextCropX);
        state.setCropY(nextCropY);
        state.setCropWidth(nextCropWidth);
        state.setCropHeight(nextCropHeight);
        int updateCount = pictureCollabStateMapper.updateById(state);
        ThrowUtils.throwIf(updateCount != 1, ErrorCode.OPERATION_ERROR, "failed to update collab state");

        // 把“原始操作”和“应用结果”一起记录下来，便于幂等和排查。
        PictureCollabOpLog opLog = new PictureCollabOpLog();
        opLog.setOpId(applyRequest.getOpId());
        opLog.setPictureId(applyRequest.getPictureId());
        opLog.setUserId(applyRequest.getUserId());
        opLog.setBaseRevision(applyRequest.getBaseRevision());
        opLog.setOpType(opType);
        opLog.setOpValue(opValue);
        opLog.setOpCropX(applyRequest.getCropX());
        opLog.setOpCropY(applyRequest.getCropY());
        opLog.setOpCropWidth(applyRequest.getCropWidth());
        opLog.setOpCropHeight(applyRequest.getCropHeight());
        opLog.setServerRevision(serverRevision);
        opLog.setResultAngle(nextAngle);
        opLog.setResultScale(nextScale);
        opLog.setResultCropX(nextCropX);
        opLog.setResultCropY(nextCropY);
        opLog.setResultCropWidth(nextCropWidth);
        opLog.setResultCropHeight(nextCropHeight);
        try {
            pictureCollabOpLogMapper.insert(opLog);
        } catch (DuplicateKeyException duplicateKeyException) {
            // 极端并发下，即使前面没查到，这里也可能撞上唯一索引，仍然按重复操作处理。
            log.warn("duplicate collab op, opId={}", applyRequest.getOpId());
            PictureCollabOpLog logByOpId = getOpLogByOpId(applyRequest.getOpId());
            ThrowUtils.throwIf(logByOpId == null, ErrorCode.OPERATION_ERROR, "failed to reload duplicate op");
            PictureCollabApplyResult result = toApplyResult(logByOpId);
            result.setDuplicated(true);
            return result;
        }
        PictureCollabApplyResult result = toApplyResult(opLog);
        result.setDuplicated(false);
        return result;
    }

    private PictureCollabState getOrCreateState(Long pictureId) {
        PictureCollabState state = pictureCollabStateMapper.selectById(pictureId);
        if (state != null) {
            return state;
        }

        // 快照不存在时延迟初始化，避免为所有图片预建协同状态。
        PictureCollabState initState = new PictureCollabState();
        initState.setPictureId(pictureId);
        initState.setRevision(0L);
        initState.setAngle(DEFAULT_ANGLE);
        initState.setScale(DEFAULT_SCALE);
        try {
            pictureCollabStateMapper.insert(initState);
            return initState;
        } catch (DuplicateKeyException duplicateKeyException) {
            // 并发初始化时，后来的线程直接回读已创建的记录即可。
            PictureCollabState currentState = pictureCollabStateMapper.selectById(pictureId);
            ThrowUtils.throwIf(currentState == null, ErrorCode.OPERATION_ERROR, "failed to init collab state");
            return currentState;
        }
    }

    private PictureCollabOpLog getOpLogByOpId(String opId) {
        return pictureCollabOpLogMapper.selectOne(new QueryWrapper<PictureCollabOpLog>()
                .eq("opId", opId)
                .last("limit 1"));
    }

    private void validateApplyRequest(PictureCollabApplyRequest applyRequest) {
        ThrowUtils.throwIf(applyRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(applyRequest.getPictureId() == null || applyRequest.getPictureId() <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(applyRequest.getUserId() == null || applyRequest.getUserId() <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(applyRequest.getOpId()), ErrorCode.PARAMS_ERROR, "opId cannot be blank");
        ThrowUtils.throwIf(applyRequest.getBaseRevision() == null || applyRequest.getBaseRevision() < 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(applyRequest.getOpType()), ErrorCode.PARAMS_ERROR);

        // 裁剪框和旋转/缩放的参数类型不同，因此分开校验。
        if (PictureCollabConstant.OP_CROP_BOX.equals(applyRequest.getOpType())) {
            validateCropBox(applyRequest);
        } else {
            ThrowUtils.throwIf(!isFinite(applyRequest.getValue()), ErrorCode.PARAMS_ERROR, "operation value is invalid");
            if (PictureCollabConstant.OP_SCALE_FACTOR.equals(applyRequest.getOpType())) {
                ThrowUtils.throwIf(applyRequest.getValue() <= 0, ErrorCode.PARAMS_ERROR, "scale factor must be greater than 0");
            }
        }
        checkPictureExists(applyRequest.getPictureId());
    }

    private void validateCropBox(PictureCollabApplyRequest applyRequest) {
        ThrowUtils.throwIf(!isFinite(applyRequest.getCropX())
                        || !isFinite(applyRequest.getCropY())
                        || !isFinite(applyRequest.getCropWidth())
                        || !isFinite(applyRequest.getCropHeight()),
                ErrorCode.PARAMS_ERROR,
                "crop box is invalid");
        ThrowUtils.throwIf(applyRequest.getCropX() < 0 || applyRequest.getCropY() < 0,
                ErrorCode.PARAMS_ERROR,
                "crop box position must be non-negative");
        ThrowUtils.throwIf(applyRequest.getCropWidth() < MIN_CROP_SIZE || applyRequest.getCropHeight() < MIN_CROP_SIZE,
                ErrorCode.PARAMS_ERROR,
                "crop box size is too small");
    }

    private boolean isFinite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    private void checkPictureExists(Long pictureId) {
        // 统一在服务层兜底图片存在性，避免协议层绕过基础校验。
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "picture not found");
    }

    private double normalizeAngle(double angle) {
        // 角度统一收敛到 [0, 360)，避免无限累加导致数值膨胀。
        double result = angle % 360;
        if (result < 0) {
            result += 360;
        }
        return result;
    }

    private double clampScale(double scale) {
        // 缩放统一做上下界保护，避免客户端传入异常倍率把图片状态冲坏。
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private PictureCollabApplyResult toApplyResult(PictureCollabOpLog opLog) {
        PictureCollabApplyResult result = new PictureCollabApplyResult();
        result.setPictureId(opLog.getPictureId());
        result.setUserId(opLog.getUserId());
        result.setOpId(opLog.getOpId());
        result.setOpType(opLog.getOpType());
        result.setOpValue(opLog.getOpValue());
        result.setBaseRevision(opLog.getBaseRevision());
        result.setServerRevision(opLog.getServerRevision());
        result.setAngle(opLog.getResultAngle());
        result.setScale(opLog.getResultScale());
        result.setCropX(opLog.getResultCropX());
        result.setCropY(opLog.getResultCropY());
        result.setCropWidth(opLog.getResultCropWidth());
        result.setCropHeight(opLog.getResultCropHeight());
        return result;
    }

    private PictureCollabStateVo toStateDTO(PictureCollabState state) {
        PictureCollabStateVo stateDTO = new PictureCollabStateVo();
        stateDTO.setPictureId(state.getPictureId());
        stateDTO.setRevision(state.getRevision());
        stateDTO.setAngle(state.getAngle());
        stateDTO.setScale(state.getScale());
        stateDTO.setCropX(state.getCropX());
        stateDTO.setCropY(state.getCropY());
        stateDTO.setCropWidth(state.getCropWidth());
        stateDTO.setCropHeight(state.getCropHeight());
        return stateDTO;
    }
}
