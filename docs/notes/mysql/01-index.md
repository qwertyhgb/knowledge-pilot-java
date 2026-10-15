# MySQL 索引

> 记录时间：2026-10-15 起 | 对应实验脚本：`src/main/resources/db/index-lab.sql`
> 实验表：`idx_lab_member`（10 万行，**非业务表**，可随意删重建）

---

## 一、我原本以为

> ⚠️ 这一栏是本文件最值钱的部分。等实验做完回来补充真实的错误认知。

1. 最左匹配是指 WHERE 条件的**书写顺序**必须和索引列顺序一致 ❗**已证伪**（实验 2 结论 1）
2. `type = ALL` 就等于这条 SQL 废了，必须优化
3. `EXPLAIN` 里的 `rows` 是实际扫描的行数 ❗**已证伪**（实验 1 知识点 A：`rows=99802` 非 100000）
4. 覆盖索引能"优化一切"，什么查询都用它 ❗**部分证伪**（实验 2 结论 5：多一个非索引列就失效）
5. ~~`IGNORE INDEX (索引名)` 能强制全表扫描~~ ❗**已证伪**（见"坑 1"）
6. ~~优化器会优先选更"完整"的联合索引~~ ❗**已证伪**（见"收获 2"）
7. ~~范围查询一定会让后面的列失效~~ ❗**已证伪**（实验 2 结论 3：优化器会换条路走）

（**实验做完后回来逐条修正**，写"错在哪 + 为什么会这么想"）

---

## 二、实验环境

| 项 | 值 |
|---|---|
| 表 | `idx_lab_member`（学习专用，不影响业务表） |
| 字段 | `id / user_id / workspace_id / role / create_time` |
| 数据量 | 100,000 行 |
| 数据分布 | **刻意倾斜**：`user_id = 1` 有 5000 条，其余用户各 1 条 |
| 索引 | `idx_user(user_id)` |
| | `idx_user_workspace(user_id, workspace_id)` |
| | `idx_ws_user(workspace_id, user_id)` |
| | `idx_create_user(create_time, user_id)` |

> 💡 **为什么要造倾斜数据**：若每个用户只有 1 条记录，按 `user_id` 查询和全表扫描耗时几乎相同，
> 索引的优劣根本测不出来。必须造出重度用户才能拉开差距。

---

## 三、实验记录

### 实验 1：读懂 EXPLAIN 的四个关键列 ✅（已跑）

**核心关注列**：`type` / `key` / `rows` / `Extra`

#### 1.1 强制全表扫描（对照组）

```sql
EXPLAIN SELECT COUNT(*)
FROM idx_lab_member USE INDEX ()
WHERE user_id = 1;
```

**实测结果（2026-10-15）**：

| 列 | 实际值 | 解读 |
|---|---|---|
| `type` | `ALL` | ✅ 全表扫描 |
| `possible_keys` | `NULL` | 没有索引可选（被 `USE INDEX ()` 屏蔽） |
| `key` | `NULL` | 未使用任何索引 |
| `key_len` | `NULL` | — |
| `ref` | `NULL` | 非 ref/eq_ref 访问方式 |
| `rows` | `99802` | **估算**要扫近10 万行 |
| `filtered` | `0.00` | 见下方知识点 B（估算失真） |
| `Extra` | `Using where` | 在扫描结果上过滤 |

#### 1.2 走索引（实验组）

```sql
EXPLAIN SELECT COUNT(*)
FROM idx_lab_member
WHERE user_id = 1;
```

预期结果 → **实测结果（2026-10-15）**：

| 列 | 实际值 | 解读 |
|---|---|---|
| `type` | `ref` | ✅ 精确匹配索引，达标 |
| `possible_keys` | `idx_user, idx_user_workspace` | 候选两个；反序索引因最左匹配失败被排除 |
| `key` | `idx_user` | 优化器选了**更短**的那个（见"收获 2"） |
| `key_len` | `8` | 只用了 user_id 一列（BIGINT = 8 字节） |
| `ref` | `const` | 表示"与常量比较"，即 `user_id = 固定值` |
| `rows` | `5001` | 只扫 5001 行 |
| `filtered` | `100.00` | 扫到的 5001 行全部保留 |
| `Extra` | **`Using index`** | ✅ **覆盖索引，不用回表！** |

#### 1.3 对比总结

| | type | possible_keys | key | key_len | rows | filtered | Extra |
|---|---|---|---|---|---|---|---|
| **全表扫描** | `ALL` | `NULL` | `NULL` | `NULL` | 99802 | 0.00 | `Using where` |
| **走索引** | `ref` | idx_user, idx_user_workspace | `idx_user` | 8 | 5001 | 100.00 | `Using index` |

**变化**：`type` 从 `ALL` → `ref`，`rows` 从 99802 → 5001（**约 20 倍差距**），
`Extra` 从 `Using where` → `Using index`（额外省掉了回表开销）。

**实验结论（自己写一句话）：**

#### 1.4 两组结果里藏着的两个知识点

**知识点 A：`rows = 99802`，为什么不是精确的 100000？** ❗

`rows` **是基于统计信息的估算值**，不是实际扫描行数。InnoDB 的行数估算来自采样统计
（`innodb_stats`），所以必然有偏差。

> ⚠️ **这直接推翻了"我原本以为"第 3 条**。想看真实行数必须用 `EXPLAIN ANALYZE`（实验 6 验证）。

**知识点 B：`filtered` 这一列，以及估算失真的极端例子** ❗

两组结果对比：

| | rows | filtered | 推算返回行数 | 是否准确 |
|---|---|---|---|---|
| 全表组 | 99802 | 0.00 | `99802 × 0 / 100` = **0** | ❌荒谬 |
| 索引组 | 5001 | 100.00 | `5001 × 100 / 100` = **5001** | ✅ 准确 |

**索引组的推算是准确的**（真实返回就是 5001 行）。

但**全表组的推算荒谬**：扫了近 10 万行，结果说"返回 0 行"？明显不对。

**为什么会失真？** 因为 `USE INDEX ()` 是**人为强制**优化器走一条它本不认可的路径。
优化器在被迫全表扫描时，统计信息模型不再适用，`filtered` 就会算出无意义的结果。

> 🎯 **这是一个绝佳的反面教材**：
> **`EXPLAIN` 只是估算；当你用 hint 强行扭曲优化器的选择时，估算可能离谱到荒谬。**
> 真实执行才靠谱（→ `EXPLAIN ANALYZE`，实验 6 验证）。

> 💡 **顺带一段面试话术**：如果面试官问"`EXPLAIN` 和 `EXPLAIN ANALYZE` 有什么区别"，
> 你现在有真实案例了 ——
> "`EXPLAIN` 给的是优化器基于统计信息的**估算**，我做强制索引对照实验时
> 遇到过 `filtered` 被算成 0 的情况，明显失真；`EXPLAIN ANALYZE` 会真正执行一遍，
> 给出真实的 rows、loops 和耗时。"

### type 的性能排序（必须背下来）

```
最优→ 最差
const → eq_ref → ref → range → index → ALL
```

| type | 含义 | 目标 |
|---|---|---|
| `const` | 命中主键/唯一索引，最多 1 行 | ✅ 极致 |
| `eq_ref` | JOIN 时主键/唯一索引关联 | ✅ 极致 |
| `ref` | 命中普通索引，返回多行 | ✅ **够用** |
| `range` | 范围扫描（`>`、`BETWEEN`、`LIKE 'x%'`） | ⚠️ 可接受 |
| `index` | 扫整个索引（仍是全扫，只是比 ALL 快） | ❌ 差 |
| `ALL` | 全表扫描 | 💀 最差 |

### Extra 关键标记速查

| Extra 值 | 含义 | 评价 |
|---|---|---|
| `Using index` | **覆盖索引**，不用回表 | ✅ 最想看到 |
| `Using index condition` | 索引条件下推（ICP） | ✅ 优化 |
| `Using where` | 在索引结果上过滤 | 正常 |
| `Using filesort` | **文件排序**，排序没用上索引 | ⚠️ 需优化 |
| `Using join buffer` | JOIN 未用上索引，块嵌套循环 | 💀 严重 |

---

### 实验 2：最左匹配（核心，面试必考）✅（已跑，2026-10-15）

**实测结果全表**：

| # | WHERE | possible_keys | key | key_len | type | rows | Extra |
|---|---|---|---|---|---|---|---|
| 1 | `user_id = 1` | 全4 个索引 | `idx_user` | 8 | ref | 5001 | `Using index` |
| 2 | `workspace_id = 100` | 2 个联合 | `idx_ws_user` | 8 | ref | 100 | `Using index` |
| 3 | `user_id=1 AND workspace_id=100` | 3 个联合 | `idx_user_workspace` | **16** | ref | 5 | `Using index` |
| 4 | `workspace_id=100 AND user_id=1` | 3 个联合 | `idx_user_workspace` | **16** | ref | 5 | `Using index` |
| 5 | `user_id=1 AND role='OWNER'` | 2 个 | `idx_user` | 8 | ref | 5001 | **`Using where`** |
| 6 | `user_id > 1 AND workspace_id=100` | 3 个联合 | `idx_ws_user` | 16 | range | 95 | `Using where` |
| 6b | `workspace_id=100 AND user_id>1` | 3 个联合 | `idx_ws_user` | 16 | range | 95 | `Using where` |
| 7 | `FORCE(idx_ws_user) WHERE user_id=1` | 1 个 | `idx_ws_user` | 16 | range | **1012** | `Using where` |

#### 结论 1：SQL 书写顺序不影响索引命中 ❗（证伪"我原本以为"第 1 条）

组 3 与组 4 条件完全相同、仅书写顺序不同，结果**完全一致**（key/key_len/type/rows 全同）。

**原因**：MySQL 优化器会把 WHERE 条件**重排**成索引列的顺序去匹配，
不关心 SQL 里 `AND` 谁先写谁后写。

**正确表述**：最左匹配的"最左"指的是**索引的列顺序**（`idx_user_workspace` 的最左是 `user_id`），
不是 SQL 的书写顺序。

> ❌ 常见错误答法："最左匹配就是 WHERE 要按 a、b 的顺序写" —— 错。

#### 结论 2：`key_len` 是判断"索引用了几列"的硬证据 💎

| key_len | 含义 |
|---|---|
| 8 | 只用了联合索引的第 1 列 |
| 16 | 第 1、2 列都用上了 |

组 3 的 `key_len=16` 证明联合索引被**完整利用**；
组 1/组 5 的 `key_len=8` 说明只用了最左列 `user_id`。

> 🎤 面试时证明"我的索引用满了"，`key_len` 比嘴说"我建了联合索引"有说服力得多。

#### 结论 3：范围查询会破坏后续列 —— 但**优化器可能换条路走**❗

组 6（`user_id > 1 AND workspace_id = 100`）的预期是"走 idx_user_workspace，
`user_id` 范围扫，`workspace_id` 失效"。但实际 `key = idx_ws_user`：

| 方案 A：走 `idx_user_workspace` | 方案 B：走 `idx_ws_user`（实际选择） |
|---|---|
| `user_id > 1` 范围扫 → 约 10 万行 | `workspace_id = 100` 精确扫 → 约 100 行 |
| 然后 `workspace_id` **完全失效** | 然后 `user_id > 1` 过滤掉一部分 |
| ❌ 代价高 | ✅ 代价低 |

**洞察**："范围查询破坏最左匹配"这句话，
**只在优化器"被迫"用那个索引时才成立**。
如果同时存在反向索引，优化器会选代价更低的那条路，"破坏"就不发生了。

> 🎯 这说明**索引设计要为查询模式服务**，不能只按"区分度"拍脑袋。

#### 结论 4：`FORCE INDEX` 强制走非最左列索引的惨重代价 💀

组 7 用 `FORCE INDEX (idx_ws_user)` 查 `user_id = 1`，与组 1（同样条件、不强制）对比：

| | 组 1（正常） | 组 7（强制反向索引） |
|---|---|---|
| type | **`ref`** | **`range`** ⚠️ |
| key_len | 8 | 16 |
| Extra | **`Using index`** | `Using where` |
| rows | 5001 | 1012（**假象**，见下） |

**`rows = 1012` 是假象**：`user_id` 在 `idx_ws_user` 里是**非最左列**，无法直接定位，
MySQL 只能扫整段索引再**逐行过滤**。1012 不是"精确定位了 1012 行"，
而是"扫了一大片，最后筛出 1012 行符合条件的"。

**验证方法**：把 `user_id` 改成 `= 5001`（只有 1 条），组 7 的 rows 仍约 1012 ——
因为它根本不按 user_id 定位。

**代价总结**：`type` 从 `ref` 降级 `range`、丢了覆盖索引、多一次回表、扫描行数不可控。

> ⚠️ **教训**：能用优化器就别 `FORCE INDEX`。
> 除非已用 `EXPLAIN ANALYZE` **证明**它选错了，才考虑强制。

#### 结论 5：覆盖索引很脆弱 —— 多一个非索引列就失效 ❗

组 1（`user_id=1`）`Extra = Using index`；
组 5（`user_id=1 AND role='OWNER'`）`Extra` 掉回 `Using where`。

**原因**：`role` 不在 `idx_user` 里，MySQL 必须**回表**取 `role` 才能过滤。

> 🎯 覆盖索引的边界：**查询所需的所有列都必须被索引覆盖**，漏一个就失效。

**实验结论（自己写三句话总结最左匹配）：**

---

### 实验 3：回表 vs 覆盖索引⬜ 待做

| # | 查询列 | 预期 Extra | 实际 Extra | 是否回表 |
|---|---|---|---|---|
| 1 | `user_id` | `Using index` | | 否 |
| 2 | `user_id, workspace_id` | `Using index` | | 否 |
| 3 | `user_id, role` | 非 `Using index` | | 是 |

**实验结论（自己写）：**

---

### 实验 4：索引失效的 5 类场景 ⬜ 待做

| # | 写法 | 预期是否失效 | 实际 key | 实际 type |
|---|---|---|---|---|
| 1 | `WHERE user_id = '1'`（字符串引号） | | | |
| 2 | `WHERE user_id + 0 = 1`（函数包裹） | | | |
| 3 | `WHERE user_id LIKE '%1'`（前导模糊） | | | |
| 4 | `WHERE user_id NOT IN (1,2,3)` | | | |
| 5 | `WHERE user_id = 1 OR role = 'MEMBER'`（OR 串非索引列） | | | |

**实验结论（自己写）：**

---

### 实验 5：ORDER BY 与 filesort ⬜ 待做

| # | ORDER BY 列 | 预期 Extra | 实际 Extra | 说明 |
|---|---|---|---|---|
| 1 | `ORDER BY user_id`（索引首列，正序） | | | |
| 2 | `ORDER BY user_id DESC`（反向扫描） | | | |
| 3 | `ORDER BY create_time`（非索引列） | | | 应出现 `Using filesort` |
| 4 | `WHERE user_id=1 ORDER BY workspace_id`（范围后失效） | | | |

**实验结论（自己写）：**

---

### 实验 6：EXPLAIN vs EXPLAIN ANALYZE ⬜ 待做

```sql
-- 对比：EXPLAIN 只给估算，ANALYZE 实际执行并给真实行数与耗时
EXPLAIN         SELECT COUNT(*) FROM idx_lab_member WHERE user_id = 1;
EXPLAIN ANALYZE SELECT COUNT(*) FROM idx_lab_member WHERE user_id = 1;
```

| 对比项 | EXPLAIN | EXPLAIN ANALYZE |
|---|---|---|
| 是否真正执行 SQL | 否，只做优化分析 | ✅ 是 |
| `rows` 是真实值吗 | ❌ 基于统计信息估算 | ✅ 真实值 |
| 能否看到耗时 | ❌ | ✅ 有 `time=` |
| 能否看到 loops | ❌ | ✅ |
| 副作用 | 无 | ⚠️ 真实执行，写操作要小心 |

**实验结论（自己写）：**

---

## 四、我踩的坑

> ⚠️ 边做边记，别事后补

### 坑1：`IGNORE INDEX (PRIMARY)` 没能强制全表扫描 ❗

**现象**：表上有 4 个索引（PRIMARY + idx_user + idx_user_workspace + idx_ws_user + idx_create_user），
写 `IGNORE INDEX (PRIMARY)` 后，EXPLAIN 结果仍然是 `type=ref`、`key=idx_user`，
完全没达到"强制全表扫描"的目的。

**根因**：`IGNORE INDEX` 的语义是"**不许用这一个索引**"，而不是"不许用任何索引"。
WHERE条件是 `user_id = 1`，而 `idx_user` 恰好能完美满足，优化器当然继续用它。

**正确写法**：
```sql
-- 写法1：把所有索引列出来（最直观）
EXPLAIN SELECT COUNT(*) FROM idx_lab_member
     IGNORE INDEX (PRIMARY, idx_user, idx_user_workspace, idx_ws_user, idx_create_user)
WHERE user_id = 1;

-- 写法2：USE INDEX()空列表 = 一个都不用
EXPLAIN SELECT COUNT(*) FROM idx_lab_member USE INDEX () WHERE user_id = 1;
```

**反思**：做"对照组实验"时要意识到——**只要表上还剩任何一个可用索引，
优化器就仍然可能走索引**。强制走某条路径必须把所有替代路径都堵死。

### 坑2：`EXPLAIN` 的执行时间不能当查询耗时看 ❗

**现象**：Navicat 里执行 `EXPLAIN SELECT ...`，显示"查询时间 0.000s"，
误以为查询本身极快。

**根因**：`EXPLAIN` **不真正执行查询**，只做优化分析，所以几乎零耗时。
想看真实耗时必须跑**不带 EXPLAIN** 的查询语句。

（MySQL 8.0 里 `SQL_NO_CACHE` 已移除，因为查询缓存默认就是关闭的，不需要也无法再手动关闭。）

### 坑3：造数脚本的边界重叠导致数据比预期多 1 条 ❗

**现象**：脚本按预期应是 `user_id = 1` 有 5000 条，实际 EXPLAIN 显示 `rows = 5001`。

**根因**：造数逻辑
```sql
CASE WHEN n <= 5000 THEN 1 ELSE n - 5000 END AS user_id
```
推演边界可知：
- `n = 1..5000` → `user_id = 1`（5000 条）
- `n = 5001` → `5001 - 5000 = 1` → **又是一个user_id = 1**（多出 1 条）

两个分支在 `n = 5001` 处重叠了。

**反思**：对索引性能而言 5000 vs 5001 没有区别，但**实验数据要精确核对**——
如果后续要下"精确匹配"这类结论，差 1 条就说明数据没有想象中干净。
真实项目里同样会踩这类坑（边界值写成 `<` 还是 `<=`）。

---

## 四·补：实验 1 的意外收获（比预期更重要）

> 这一节是 2026-10-15 实际跑实验时发现的，比原计划的实验内容更有价值，单独记录。

### 收获 1：`possible_keys` 提前暴露了「最左匹配」

强制全表失败的EXPLAIN 里，`possible_keys` 显示的是：

```
possible_keys: idx_user, idx_user_workspace
```

**注意 `idx_ws_user` 和 `idx_create_user` 都不在列表里！**

**原因**：WHERE 只有 `user_id = 1`，而这两个索引的**最左列**分别是
`workspace_id` 和 `create_time`，都与条件对不上 → **最左匹配失败，优化器认为它们根本用不了。**

> 💡 **这就是实验 2 要验证的核心现象**：
> 索引能不能用，**取决于最左列是否被条件命中**，而不是"哪列相关度更高"。
> 建两个反序索引做对照，就是为了把这个现象定量跑出来。

### 收获 2：优化器选索引看的是「单位行代价」，不是「索引是否更全」

直觉会以为优化器会选更"完整"的 `idx_user_workspace`，但它选了更短的 `idx_user`：

| | `idx_user` | `idx_user_workspace` |
|---|---|---|
| `key_len` | **8**（只存 user_id） | 16（user_id + workspace_id） |
| 扫描代价 | 更低 | 更高 |
| 优化器判断 | 5001 行 × 8 字节 | 5001 行 × 16 字节 |

**根因**：查询是 `COUNT(*)`，不需要 `workspace_id` 列，多扫那 8 字节纯属浪费。

> 🎤 面试加分点：多数人以为"优化器倾向选最长/最全的索引"，
> 实际上它选的是**单位行代价最小**的那个。

### 收获 3：`Extra = Using index` 说明这是覆盖索引

查询是 `SELECT COUNT(*)`，**一个列都不需要返回**。
二级索引里已经包含定位到这 5001 行所需的全部信息，因此**根本不用回聚簇索引**。

对照实验（会回表，Extra 不含 `Using index`）：
```sql
EXPLAIN SELECT * FROM idx_lab_member WHERE user_id = 1;
```

**结论**：`COUNT(*)`、`COUNT(1)`、`COUNT(常量列)` 这类查询天然容易命中覆盖索引，
而 `SELECT *` 则几乎必然回表 —— 这是"能不能用上覆盖索引"最直观的分界线。

---

## 五、面试怎么答

### 追问链 1：联合索引最左匹配（必考）

#### Q1：什么是联合索引的最左匹配原则？

> A：联合索引 `(a, b)` 的排序规则是**先按 a 排，a 相同时再按 b 排**，类似电话簿
> （先按姓排，姓相同再按名排）。所以查询必须从最左列 `a` 开始**连续匹配**，
> 跳过 `a` 直接查 `b`，在 B+ Tree 里无法定位，只能全表扫描。

#### Q2：为什么会有这个原则？（考底层，多数人答不上来）

> A：因为 B+ Tree 的**叶子节点是按 `(a, b)` 字典序物理排列的**。
> - 知道 `a` 的值 → 可以二分定位到 `a` 的区间，再在区间内找 `b`；
> - 只知道 `b` → 整个索引里 `b` 分散在各个 `a` 区间内，只能逐段扫，退化成全扫。
>
> 所以这不是人为规定，是**索引物理结构决定的**。

> 💡 **加分答法（带上实验证据）**：
> "我在 `idx_ws_user(workspace_id, user_id)` 上强制查 `user_id`（非最左列），
> 结果 `type` 从 `ref` 降级成 `range`，`key_len` 从 8 变成 16，扫了 1012 行才筛出结果，
> 还丢了 `Using index` 覆盖索引。这就是最左匹配的物理限制的直接证据。"

#### Q3：`WHERE b=2 AND a=1` 能走 `idx(a,b)` 吗？（陷阱题）

> A：**能**。我在实验里构造了完全相同的两个查询，只改书写顺序：
> - 组 3：`WHERE user_id = 1 AND workspace_id = 100`
> - 组 4：`WHERE workspace_id = 100 AND user_id = 1`
>
> 两条的 `key`、`key_len`、`type`、`rows` **完全一致**（都是 `idx_user_workspace`、16、ref、5）。
> 因为 **MySQL 优化器会把 WHERE 条件重排成索引列的顺序去匹配**，
> 不关心 SQL 里 `AND` 谁先写。

> ❌ **常见错误答法**："最左匹配就是 WHERE 要按 a、b 的顺序写" —— **错**，
> 这是流传最广的误解，我实验里已经证伪了。

#### Q4：那什么时候最左匹配真的"断"了？

> A：两种情况：
> 1. **最左列完全没出现在 WHERE 里** —— 例如 `idx(a,b)` 只查 `b`，
>    优化器认为它连候选资格都没有（我在 `possible_keys` 里验证过这个现象）；
> 2. **最左列是范围查询**，导致后面的列失效 —— 例如 `WHERE a > 1 AND b = 2`，
>    `b` 用不上（组 6 如果只有 `idx_user_workspace` 就是这个情况）。

#### Q5：联合索引的列顺序该怎么排？

> A：**不是单纯看区分度，要结合查询模式**。
>
> 常见误区是"把区分度高的列放前面"，但如果实际业务里查询总是从低区分度列开始，
> 那样反而浪费了 B+ Tree 的层级剪枝能力。
>
> 正确做法：**看谁最常作为第一个过滤条件，谁放最左**。
>
> 💡 **我的实验给了现成案例**：组 6 的查询是 `user_id > 1 AND workspace_id = 100`，
> 表上同时有 `idx_user_workspace(user_id, workspace_id)` 和 `idx_ws_user(workspace_id, user_id)`。
> 如果只有前者，`workspace_id` 会失效；但因为存在反向索引，
> 优化器选了后者，用 `workspace_id = 100` 精确定位到约 100 行，代价远低。
>
> **结论：索引设计要为查询模式服务，不能只按区分度拍脑袋。**

#### Q6：什么是"范围查询破坏最左匹配"？这句话总成立吗？

> A：**范围查询会让它后面的索引列失效**，因为索引里那些列在范围内是无序的。
>
> 但这句话**有前提**：它成立的前提是"优化器被迫只能用这一个索引"。
> 如果表上存在**反向索引**，优化器会算代价选另一条路，"破坏"就不发生了。
>
> 我在组 6 验证过：`WHERE user_id > 1 AND workspace_id = 100`
> 最终 `key = idx_ws_user`，用 `workspace_id` 精确匹配，
> 而不是走 `idx_user_workspace` 然后让 `workspace_id` 失效。

#### Q7（深挖）：数据量到千万级，这套索引还够用吗？

> A：需要分情况看：
> - **树高变化**：10 万行 B+ Tree 约 3 层，千万行约 4 层，多一次磁盘 IO，
>   但索引本身仍然有效；
> - **数据倾斜**：我造的数据里 `user_id = 1` 有 5001 条，量级差异明显。
>   千万级时倾斜会更严重，这是比索引更棘手的瓶颈；
> - **真正的解法**：到这个量级通常要考虑**分库分表**或**历史数据归档**，
>   而不是继续加索引。加索引能解决"查得慢"，解决不了"单表太大"。
>
> 所以我的判断是：**索引优化有明确的适用范围，超范围后要换手段。**

#### Q8（深挖）：`FORCE INDEX` 能用吗？

> A：能用，但要谨慎。我在组 7 做过强制反向索引的实验，代价很明显：
>
> | | 正常（组 1） | 强制反向（组 7） |
> |---|---|---|
> | type | `ref` | `range` ⚠️ |
> | key_len | 8 | 16 |
> | Extra | `Using index` | `Using where` |
> | rows | 5001 | 1012（假象） |
>
> `type` 降级、覆盖索引丢失、多一次回表，而且 `rows=1012` 是假象 ——
> 它不是"精确定位 1012 行"，而是"扫了一大片再逐行筛出 1012 行"。
>
> **结论：能用优化器就别 `FORCE INDEX`**。除非已经用 `EXPLAIN ANALYZE`
> 明确证明了优化器选错，才考虑人工指定。

---

### 追问链 2：EXPLAIN 与索引诊断

#### Q1：怎么判断一条 SQL 有没有走索引？

> A：看 `EXPLAIN` 的 `type` 和 `key` 两列。`type = ALL` 是全表扫描，
> `key = NULL` 说明完全没走索引。日常还要配合 `EXPLAIN ANALYZE` 看真实行数和耗时，
> 因为 `EXPLAIN` 的 `rows` 只是基于统计信息的估算值。

#### Q2：`rows` 是准确的吗？

> A：**不是**。它是基于 InnoDB 采样统计的估算值。
> 我的实验里强制全表扫描时 `rows = 99802`，而实际是 100000 行，有明显偏差。
> 看真实值必须用 `EXPLAIN ANALYZE`。

#### Q3：`EXPLAIN` 和 `EXPLAIN ANALYZE` 有什么区别？

> A：`EXPLAIN` **不真正执行 SQL**，只给出优化器基于统计信息的估算执行计划。
> `EXPLAIN ANALYZE` 会**真正执行一遍**，给出真实的 `rows`、`loops`、`filters` 和耗时。
>
> 💡 **我的真实案例**：我用 `USE INDEX ()` 强制全表扫描时，
> `EXPLAIN` 给出的 `filtered = 0.00`，按 `rows × filtered` 推算返回 0 行 —— 明显失真。
> 因为 hint 强行扭曲了优化器的选择，统计信息模型就不适用了。
> 这时候只有 `EXPLAIN ANALYZE` 的可信。

#### Q4：type 有哪些值？性能排序？

> A：从优到劣 `const`、`eq_ref`、`ref`、`range`、`index`、`ALL`。
> 业务查询至少要达到 `ref`，`range` 是可接受下限，
> 出现 `index` 或 `ALL` 基本说明这条 SQL 需要加索引或改写。

#### Q5：什么是覆盖索引？怎么判断？

> A：二级索引的叶子节点只存了**索引列 + 主键**。如果查询需要的所有列
> 索引里都有，就不用回聚簇索引查完整数据，这叫覆盖索引。
> **判断标志是 `Extra` 出现 `Using index`**。
>
> 我的实验里：`SELECT COUNT(*) WHERE user_id = 1` 是 `Using index`（覆盖），
> 但只要加一个 `AND role = 'OWNER'`，因为 `role` 不在索引里，就必须回表，
> `Extra` 立刻掉回 `Using where`。

#### Q6：怎么避免回表？

> A：让二级索引包含查询所需的**所有列**。但这会让索引体积变大，
> 写入和维护代价上升，本质是**空间换时间**，要权衡。
> 不能无脑建宽索引。

#### Q7（深挖）：为什么 `COUNT(*)` 特别容易命中覆盖索引？

> A：因为它**不需要返回任何列的值**，只需要计数。
> 索引里已经包含定位这些行所需的全部信息，所以天然不用回表。
> 而 `SELECT *` 要返回所有列，几乎必然回表。
> 这是"能不能用上覆盖索引"最直观的分界线。

---

### 追问链 3：索引设计与业务决策

#### Q1：你是怎么给 KnowledgePilot 设计索引的？

> A：**先用实验证明，再动业务代码**。
> 我构造了一张 `idx_lab_member` 实验表，索引结构照抄 `workspace_member`，
> 灌了 10 万行**刻意倾斜**的数据（让 `user_id = 1` 有 5001 条，
> 否则每个用户只 1 条记录的话，索引和全表扫描耗时没差异，实验白做）。
>
> 然后系统验证了最左匹配、覆盖索引、索引失效、`FORCE INDEX` 代价这些场景。
> 实验结论确认可行后，才会考虑改业务表。

#### Q2：那你在项目里发现什么真实问题了吗？

> A：有。`selectPageByMember` 的 SQL 里有
> `ORDER BY m.create_time DESC, w.id DESC`，而 `create_time`
> **不在任何现有索引里**。用户加入空间多时，排序必然出现 `Using filesort`。
>
> 我计划在实验表上验证：把索引改成 `(user_id, create_time)` 后，
> 排序能否直接利用索引顺序、`filesort` 是否消失、收益有多大。
>
> 但**我还没动手改业务表** —— 因为按"最小可用 → 出现问题 → 分析问题 → 再升级"的原则，
> 必须先有真实慢查询的数据证明这是瓶颈，不能凭感觉优化。

#### Q3（深挖）：为什么不直接改业务表加索引？

> A：三个理由：
> 1. **没有真实压力**：目前数据量小，加索引可能反而增加写入开销；
> 2. **没有量化依据**：不知道 filesort 到底占多少耗时，优化了也可能无感；
> 3. **索引有代价**：每个索引都占空间、拖慢写入、增加维护成本。
>
> 所以正确顺序是：**先复现问题 → 用数据证明瓶颈 → 实验验证方案 → 再落地**。

#### Q4（深挖）：如果数据倾斜严重怎么办？

> A：我实验里就故意造了倾斜数据（`user_id = 1` 有 5001 条）。
> 倾斜带来的问题是：某些 key 的索引区间特别大，查询退化成范围扫。
>
> 解法要看场景：
> - 如果是热点数据，考虑**缓存**；
> - 如果是分页深翻页，考虑**游标分页**替代 `LIMIT offset`；
> - 如果单 key 数据量本身就该很大，考虑**按时间/维度拆分**。
>
> 单纯加索引解决不了倾斜问题。

---

## 六、❓ 待解答 / 以后要验证

- [ ] 为什么 `ORDER BY` 的 `create_time` 不在 `idx_user` 里就会 filesort？B+ Tree 叶子节点是有序的，为什么不能直接利用？
- [ ] MySQL 什么情况下明明有索引却选`ALL`？怎么用 `optimizer_trace` 看优化器的决策过程？
- [ ] 覆盖索引一定比回表快吗？什么情况下反而更慢（回表随机 IO vs 索引体积）？
- [ ] 范围查询之后索引失效，那么 `WHERE a > 1 AND b = 2` 优化后的查询逻辑长什么样？
- [ ] 给 `selectPageByMember` 的 `ORDER BY m.create_time DESC, w.id DESC` 加上联合索引能消除 filesort 吗？收益多大？
- [ ] 统计信息过期会导致优化器选错索引吗？怎么手动 `ANALYZE TABLE`？

---

## 七、还没学到

- [ ] B+ Tree 树高、页大小、扇区与磁盘 IO 的关系
- [ ] 聚簇索引 vs 二级索引的叶子节点到底存了什么（要画图理解）
- [ ] 索引下推（ICP）的原理
- [ ] 前缀索引
- [ ] 索引下推与覆盖索引的区别

---

## 八、和项目的关系

> 本节让笔记和KnowledgePilot 真实业务挂钩，避免变成脱离实践的抄书笔记。

| 关联点 | 说明 |
|---|---|
| 相关业务代码 | `mapper/WorkspaceMapper.java` → `selectPageByMember`（JOIN +分页 + ORDER BY） |
| 真实问题 | 该SQL 的 `ORDER BY m.create_time DESC, w.id DESC` 中，`create_time` **不在**任何现有索引中，用户加入空间多时必然 `Using filesort` |
| 学到能用上的决策 | 可考虑加 `idx_user_create(user_id, create_time)` 消除 filesort |
| 实验表与业务表的关系 | `idx_lab_member` 的索引结构照抄 `workspace_member`，实验结论**可直接迁移**到业务表 |

> ⚠️ 但按`AGENTS.md` 第 6 节"禁止提前优化"：**先在实验表用数据证明 filesort 确实是瓶颈，
> 再决定要不要改业务表**。没有实际慢查询数据支撑，不动手改业务代码。