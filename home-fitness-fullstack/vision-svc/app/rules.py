"""确定性后处理规则：从感知到的房间事实推导可行动作清单与安全分。

占位推理（inference.py）和 JoyAI-VL 推理（joyai_vl.py）共用这套规则——
模型只负责"看"（面积/光线/障碍物），动作可行性与安全判断始终走这里，
保证两条推理路径的输出口径一致、且安全逻辑不依赖模型自由发挥。
"""
from __future__ import annotations

from typing import List, Tuple

from .schema import DiscouragedAction, Obstacle

# 推荐 / 不适合动作（根据面积估算分档）
ALL_ACTIONS = ["squat", "stretch", "bridge", "plank", "pushup", "lunge", "jumpingJack"]


def action_rules(
    area: float, lighting: str, obstacles: List[Obstacle]
) -> Tuple[List[str], List[DiscouragedAction], List[str]]:
    """根据面积 / 光线 / 障碍物推导可行动作清单。"""
    static_actions = ["stretch", "plank", "bridge"]  # 几乎所有房间都行
    recommended = list(static_actions)
    discouraged: List[DiscouragedAction] = []
    warnings: List[str] = []

    if area >= 5.0:
        recommended += ["squat", "pushup"]
    if area >= 7.0:
        recommended += ["lunge"]
    if area >= 10.0:
        recommended.append("jumpingJack")
    else:
        discouraged.append(DiscouragedAction(action="jumpingJack",
                                             reason=f"可用面积约 {area:.1f}㎡，跳跃动作不安全"))

    near_obstacle = any(o.distanceM is not None and o.distanceM < 1.0 for o in obstacles)
    if near_obstacle:
        if "lunge" in recommended:
            recommended.remove("lunge")
        discouraged.append(DiscouragedAction(action="lunge", reason="周围障碍物过近，弓步蹲不安全"))
        warnings.append("障碍物距离 < 1m，请清理周边再训练")

    if lighting == "poor":
        warnings.append("光线很差，姿态识别可能不稳定，建议增加照明")

    # 去重保序
    seen = set()
    dedup_recommended: List[str] = []
    for a in recommended:
        if a not in seen:
            seen.add(a)
            dedup_recommended.append(a)

    return dedup_recommended, discouraged, warnings


def safety_score(area: float, lighting: str, obstacles: List[Obstacle]) -> int:
    """0~100 的环境安全分。"""
    score = 100
    if area < 4:
        score -= 20
    elif area < 6:
        score -= 10
    if lighting == "dim":
        score -= 5
    elif lighting == "poor":
        score -= 15
    near = sum(1 for o in obstacles if o.distanceM is not None and o.distanceM < 1.0)
    score -= near * 10
    return max(0, min(100, score))
