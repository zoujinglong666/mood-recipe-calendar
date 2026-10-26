# -*- coding: utf-8 -*-
"""Fix remaining rollback-only pollution: FamilyService idempotent-return + OperationalEventService.record."""
import io, sys

BASE = r"C:\Users\DELL\DoubaoWork\chats\2026-09-01\new-chat\mood-recipe-calendar\backend\src\main\java\com\moodrecipe\backend"

def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()

def write(p, s):
    with io.open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(s)

# ---------- 1. FamilyService: conflict -> throw (rollback), idempotency moved to controller ----------
p1 = BASE + r"\service\FamilyService.java"
s1 = read(p1)

old_create = """        FamilyMember owner = new FamilyMember();
        owner.setGroupId(group.getId());
        owner.setOpenid(openid);
        owner.setRole("OWNER");
        try {
            members.save(owner);
        }
        catch (DataIntegrityViolationException e) {
            // 并发创建：唯一键拦截第二笔。必须显式回滚本事务刚插入的组（否则留下无 owner 的孤儿组），
            // 再返回已存在的家庭，把冲突转成幂等成功。
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            FamilyMember already = members.findByOpenid(openid).orElse(null);
            if (already != null) return viewOf(already);
            throw e;
        }
        return viewOf(openid, group);"""
new_create = """        FamilyMember owner = new FamilyMember();
        owner.setGroupId(group.getId());
        owner.setOpenid(openid);
        owner.setRole("OWNER");
        // 并发创建冲突（唯一键拦截第二笔）：DataIntegrityViolationException 默认回滚整个事务，
        // 不会留下无 owner 的孤儿组。异常向上抛，由 controller 重新查询返回已存在的家庭（幂等成功）。
        // 注意：不能在事务内 setRollbackOnly() 后又 return——Spring 提交时必抛
        // UnexpectedRollbackException（线上「锅仔后端 500 告警」同类根因，2026-10-26 修复）。
        members.save(owner);
        return viewOf(openid, group);"""
if old_create not in s1:
    print("ERROR: create block not found in FamilyService")
    sys.exit(1)
s1 = s1.replace(old_create, new_create)

old_join = """        FamilyMember member = new FamilyMember();
        member.setGroupId(group.getId());
        member.setOpenid(openid);
        member.setRole("MEMBER");
        try {
            members.save(member);
        }
        catch (DataIntegrityViolationException e) {
            // 并发加入：已在家庭里则幂等返回（本事务无其他写入，回滚无害）；否则原样抛出
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            FamilyMember already = members.findByOpenid(openid).orElse(null);
            if (already != null) return viewOf(already);
            throw e;
        }
        return viewOf(member);"""
new_join = """        FamilyMember member = new FamilyMember();
        member.setGroupId(group.getId());
        member.setOpenid(openid);
        member.setRole("MEMBER");
        // 并发加入冲突：同上抛给 controller 幂等重查；本事务无其他写入，回滚无害。
        members.save(member);
        return viewOf(member);"""
if old_join not in s1:
    print("ERROR: join block not found in FamilyService")
    sys.exit(1)
s1 = s1.replace(old_join, new_join)

# remove now-unused TransactionAspectSupport import
if "TransactionAspectSupport.currentTransactionStatus()" not in s1:
    s1 = s1.replace("import org.springframework.transaction.interceptor.TransactionAspectSupport;\n", "")
write(p1, s1)
print("OK: FamilyService.create/join fixed")

# ---------- 2. FamilyController: catch DataIntegrityViolationException -> idempotent myFamily ----------
p2 = BASE + r"\controller\FamilyController.java"
s2 = read(p2)
old_c = """    @PostMapping
    public ApiResponse<?> create(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        try {
            return ApiResponse.ok(family.create(openid));
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }"""
new_c = """    @PostMapping
    public ApiResponse<?> create(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid) {
        try {
            return ApiResponse.ok(family.create(openid));
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发创建唯一键冲突：事务已回滚（无孤儿组），重新查询返回已存在的家庭（幂等成功）
            return ApiResponse.ok(family.myFamily(openid));
        }
    }"""
if old_c not in s2:
    print("ERROR: create controller block not found")
    sys.exit(1)
s2 = s2.replace(old_c, new_c)

old_j = """    @PostMapping("/join")
    public ApiResponse<?> join(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody JoinRequest request) {
        try {
            return ApiResponse.ok(family.join(openid, request == null ? null : request.code()));
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }"""
new_j = """    @PostMapping("/join")
    public ApiResponse<?> join(@RequestAttribute(SessionAuthInterceptor.OPENID_ATTRIBUTE) String openid, @RequestBody JoinRequest request) {
        try {
            return ApiResponse.ok(family.join(openid, request == null ? null : request.code()));
        }
        catch (IllegalStateException e) {
            return ApiResponse.error(400, e.getMessage());
        }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 并发加入唯一键冲突：事务已回滚，重新查询返回当前家庭（幂等成功）
            return ApiResponse.ok(family.myFamily(openid));
        }
    }"""
if old_j not in s2:
    print("ERROR: join controller block not found")
    sys.exit(1)
s2 = s2.replace(old_j, new_j)
write(p2, s2)
print("OK: FamilyController idempotent catch added")

# ---------- 3. OperationalEventService.record -> REQUIRES_NEW ----------
p3 = BASE + r"\service\OperationalEventService.java"
s3 = read(p3)
old_e = """import org.springframework.stereotype.Service;
@Service public class OperationalEventService {
  private final OperationalEventRepository repository;
  public OperationalEventService(OperationalEventRepository repository) { this.repository = repository; }
  public void record(String type, String severity, String openid, String orderNo, String detail) {"""
new_e = """import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
@Service public class OperationalEventService {
  private final OperationalEventRepository repository;
  public OperationalEventService(OperationalEventRepository repository) { this.repository = repository; }
  /** REQUIRES_NEW：在支付回调等外层事务内落事件日志时，事件写入失败只回滚事件自身，
   *  不污染外层事务——否则 save 异常被调用方 catch 吞掉后，外层提交必抛
   *  UnexpectedRollbackException（「锅仔后端 500 告警」同类根因，2026-10-26 修复）。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String type, String severity, String openid, String orderNo, String detail) {"""
if old_e not in s3:
    print("ERROR: OperationalEventService.record block not found")
    sys.exit(1)
s3 = s3.replace(old_e, new_e)
write(p3, s3)
print("OK: OperationalEventService.record REQUIRES_NEW")

print("ALL DONE")
