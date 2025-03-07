package com.yaz.kyotaidoshin.core.service;


import com.yaz.kyotaidoshin.persistence.model.Expense;
import com.yaz.kyotaidoshin.persistence.repository.ExpenseRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ExpenseService {

  private final ExpenseRepository repository;

  public Uni<Long> countByReceipt() {
    return repository.count();
  }

  public Uni<List<Expense>> readByReceipt(long receiptId) {
    return repository.readByReceipt(receiptId);
  }

  public Uni<Integer> delete(Expense.Keys keys) {
    return repository.delete(keys.id());
  }

  public Uni<Long> countByReceipt(long receiptId) {
    return repository.countByReceipt(receiptId);
  }

  public Uni<Optional<Expense>> read(Expense.Keys keys) {
    return repository.read(keys.id());
  }

  public Uni<Expense> get(Expense.Keys keys) {
    return read(keys)
        .map(opt -> opt.orElseThrow(() -> new IllegalArgumentException("Expense not found")));
  }

  public Uni<Expense> create(Expense expense) {
    return repository.insert(expense)
        .map(id -> expense.toBuilder().id(id).build());
  }

  public Uni<Expense> update(Expense expense) {
    return repository.update(expense)
        .replaceWith(expense);
  }

  public Uni<Integer> deleteByReceipt(String buildingId, long receiptId) {
    return repository.deleteByReceipt(buildingId, receiptId);
  }
}
