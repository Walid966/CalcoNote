const STORAGE_KEY = "hesba-history-v1";
const state = {
  current: "0",
  firstOperand: null,
  operator: null,
  waitingForSecond: false,
  expression: "",
  draftNotes: { left: "", right: "" },
  ledgerOperation: null,
  ledgerOperations: [],
  history: loadHistory(),
  activeNote: null
};
state.ledgerOperation = state.history[0] || null;
state.ledgerOperations = state.ledgerOperation ? [state.ledgerOperation] : [];

const displayValue = document.querySelector("#displayValue");
const expressionDisplay = document.querySelector("#expression");
const historyList = document.querySelector("#historyList");
const historyCount = document.querySelector("#historyCount");
const emptyState = document.querySelector("#emptyState");
const noteModal = document.querySelector("#noteModal");
const noteInput = document.querySelector("#noteInput");
const modalNumber = document.querySelector("#modalNumber");
const currentNoteField = document.querySelector("#currentNoteField");
const currentNoteInput = document.querySelector("#currentNoteInput");
const draftLedger = document.querySelector("#draftLedger");
const lastResult = document.querySelector("#lastResult");
const lastResultValue = document.querySelector("#lastResultValue");
const toast = document.querySelector("#toast");

function loadHistory() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
  } catch (error) {
    return [];
  }
}

function saveHistory() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state.history));
}

function formatNumber(value) {
  if (!Number.isFinite(value)) return "خطأ";
  return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(8)));
}

function updateDisplay() {
  displayValue.textContent = state.current;
  expressionDisplay.textContent = state.expression || "جاهز للحساب";
  const canAddNote = state.current !== "0" && !state.waitingForSecond || state.waitingForSecond && state.operator !== null;
  currentNoteField.hidden = !canAddNote;
  currentNoteField.querySelector("span").innerHTML = `<b>✎</b> ${state.waitingForSecond ? "ملاحظة الرقم التالي" : "ملاحظة الرقم الحالي"}`;
  currentNoteInput.placeholder = state.waitingForSecond ? "اكتب ملاحظة الرقم الثاني قبل أو بعد كتابته..." : "اكتب الرقم ده بتاع إيه؟";
  const side = state.firstOperand === null ? "left" : "right";
  currentNoteInput.value = state.draftNotes[side] || "";
  renderDraftLedger();
}

function renderDraftLedger() {
  const hasCurrentNumber = state.firstOperand === null && !state.waitingForSecond && state.current !== "0";
  const hasDraft = state.firstOperand !== null || state.draftNotes.left || state.draftNotes.right || hasCurrentNumber;
  draftLedger.hidden = false;
  draftLedger.innerHTML = "";
  draftLedger.classList.toggle("draft-ledger-complete", Boolean(state.ledgerOperation));
  if (state.ledgerOperation) {
    const title = document.createElement("span");
    title.className = "draft-ledger-title";
    title.textContent = "سجل الملاحظات الحالي";
    draftLedger.appendChild(title);
    const operations = state.ledgerOperations.length ? state.ledgerOperations : [state.ledgerOperation];
    const hasActiveNextNumber = hasDraft && state.firstOperand !== null;
    operations.forEach((operation, index) => {
      const isLastOperation = index === operations.length - 1;
      const hideEquals = index < operations.length - 1 || (isLastOperation && hasActiveNextNumber);
      appendLedgerOperation(operation, hideEquals, index > 0);
    });
    if (hasDraft && state.firstOperand !== null) {
      const activeRow = document.createElement("div");
      activeRow.className = "ledger-operation";
      draftLedger.appendChild(activeRow);
      const activeOperator = document.createElement("span");
      activeOperator.className = "draft-operator";
      activeOperator.textContent = operatorSymbol(state.operator);
      activeRow.appendChild(activeOperator);
      if (!state.waitingForSecond) {
        appendLedgerEntry(state.current, state.draftNotes.right, null, activeRow);
      }
    }
    return;
  }
  if (!hasDraft && !state.ledgerOperation) {
    const emptyMessage = document.createElement("span");
    emptyMessage.className = "draft-empty";
    emptyMessage.textContent = "اكتب أول رقم، وملاحظته هتظهر هنا وتفضل محفوظة قدامك.";
    draftLedger.appendChild(emptyMessage);
    return;
  }

  const entries = [];
  if (state.firstOperand !== null || state.draftNotes.left || hasCurrentNumber) {
    const leftNumber = state.firstOperand === null ? state.current : formatNumber(state.firstOperand);
    entries.push({ number: leftNumber, note: state.draftNotes.left });
  }
  if (state.operator) entries.push({ operator: operatorSymbol(state.operator) });
  if (state.firstOperand !== null && !state.waitingForSecond) {
    entries.push({ number: state.current, note: state.draftNotes.right });
  }
  entries.forEach((entry) => {
    if (entry.operator) {
      const operator = document.createElement("span");
      operator.className = "draft-operator";
      operator.textContent = entry.operator;
      draftLedger.appendChild(operator);
      return;
    }
    const item = document.createElement("span");
    item.className = "draft-entry";
    const number = document.createElement("strong");
    number.className = "draft-number";
    number.textContent = entry.number;
    item.appendChild(number);
    if (entry.note) {
      const note = document.createElement("span");
      note.className = "draft-note";
      note.textContent = `✎ ${entry.note}`;
      item.appendChild(note);
    }
    draftLedger.appendChild(item);
  });
}

function appendLedgerOperation(operation, hideEquals = false, hideLeft = false) {
  const operationRow = document.createElement("div");
  operationRow.className = "ledger-operation";
  draftLedger.appendChild(operationRow);
  const entries = [
    ...(hideLeft ? [] : [{ number: operation.left, note: operation.notes.left, side: "left" }]),
    { operator: operatorSymbol(operation.operator) },
    { number: operation.right, note: operation.notes.right, side: "right" },
    ...(hideEquals ? [] : [{ operator: "=" }, { number: operation.result, editable: false }])
  ];
  entries.forEach((entry) => {
    if (entry.operator) {
      const operator = document.createElement("span");
      operator.className = "draft-operator";
      operator.textContent = entry.operator;
      operationRow.appendChild(operator);
      return;
    }
    appendLedgerEntry(entry.number, entry.note, null, operationRow, operation.id, entry.side, entry.editable !== false);
  });
}

function appendLedgerEntry(numberValue, noteValue, operator, parent = draftLedger, operationId = null, side = null, showNoteHint = true) {
    const item = document.createElement("span");
    item.className = "draft-entry";
    if (operationId && side) {
      item.classList.add("draft-entry-editable");
      item.dataset.id = operationId;
      item.dataset.side = side;
      item.title = "اضغط لتعديل الملاحظة";
    }
    const number = document.createElement("strong");
    number.className = "draft-number";
    number.textContent = numberValue;
    item.appendChild(number);
    if (noteValue) {
      const note = document.createElement("span");
      note.className = "draft-note";
      note.textContent = noteValue;
      item.appendChild(note);
    }

    if (showNoteHint) {
      const editIcon = document.createElement("span");
      editIcon.className = "draft-edit-icon";
      editIcon.textContent = "✎";
      editIcon.title = noteValue ? "تعديل الملاحظة" : "إضافة ملاحظة";
      item.appendChild(editIcon);
    }
    parent.appendChild(item);
    if (operator) {
      const operatorElement = document.createElement("span");
      operatorElement.className = "draft-operator";
      operatorElement.textContent = operatorSymbol(operator);
      parent.appendChild(operatorElement);
    }
}

function resetCalculator() {
  state.current = "0";
  state.firstOperand = null;
  state.operator = null;
  state.waitingForSecond = false;
  state.expression = "";
  state.draftNotes = { left: "", right: "" };
  state.ledgerOperation = null;
  state.ledgerOperations = [];
  lastResult.hidden = true;
  updateDisplay();
}

function inputDigit(digit) {
  const startingNewCalculation = state.firstOperand === null && state.operator === null && (state.waitingForSecond || state.current === "0");
  if (startingNewCalculation) {
    state.ledgerOperation = null;
    state.ledgerOperations = [];
    state.draftNotes = { left: "", right: "" };
    state.expression = "";
  }
  if (state.waitingForSecond) {
    state.current = digit;
    state.waitingForSecond = false;
  } else {
    state.current = state.current === "0" ? digit : state.current + digit;
  }
  updateDisplay();
}

function inputDecimal() {
  const startingNewCalculation = state.firstOperand === null && state.operator === null && (state.waitingForSecond || state.current === "0");
  if (startingNewCalculation) {
    state.ledgerOperation = null;
    state.ledgerOperations = [];
    state.draftNotes = { left: "", right: "" };
    state.expression = "";
  }
  if (state.waitingForSecond) {
    state.current = "0.";
    state.waitingForSecond = false;
  } else if (!state.current.includes(".")) {
    state.current += ".";
  }
  updateDisplay();
}

function chooseOperator(operator) {
  const currentValue = Number(state.current);
  if (state.operator && state.waitingForSecond) {
    state.operator = operator;
    state.expression = `${formatNumber(state.firstOperand)} ${operatorSymbol(operator)}`;
    updateDisplay();
    return;
  }
  if (state.firstOperand === null) state.firstOperand = currentValue;
  else if (state.operator) {
    const operation = commitOperation(state.firstOperand, currentValue, state.operator);
    if (!operation) return;
    state.current = operation.result;
    state.firstOperand = Number(operation.result);
    state.draftNotes = { left: "", right: "" };
  }
  state.operator = operator;
  state.waitingForSecond = true;
  state.expression = `${formatNumber(state.firstOperand)} ${operatorSymbol(operator)}`;
  updateDisplay();
}

function calculate(left, right, operator) {
  if (operator === "+") return left + right;
  if (operator === "-") return left - right;
  if (operator === "*") return left * right;
  if (operator === "/") return right === 0 ? NaN : left / right;
  return right;
}

function operatorSymbol(operator) {
  return { "+": "+", "-": "−", "*": "×", "/": "÷" }[operator] || operator;
}

function commitOperation(left, right, operator) {
  const result = calculate(left, right, operator);
  if (!Number.isFinite(result)) {
    showToast("لا يمكن القسمة على صفر");
    return null;
  }
  const operation = {
    id: Date.now(),
    left: formatNumber(left),
    right: formatNumber(right),
    operator,
    result: formatNumber(result),
    notes: { ...state.draftNotes },
    createdAt: new Date().toLocaleTimeString("ar-EG", { hour: "2-digit", minute: "2-digit" })
  };
  state.history.unshift(operation);
  state.history = state.history.slice(0, 12);
  saveHistory();
  renderHistory();
  state.ledgerOperation = operation;
  state.ledgerOperations.push(operation);
  lastResultValue.textContent = operation.result;
  lastResult.hidden = false;
  return operation;
}

function completeCalculation() {
  if (state.operator === null || state.firstOperand === null) return;
  const left = state.firstOperand;
  const right = Number(state.current);
  const operation = commitOperation(left, right, state.operator);
  if (!operation) return;
  state.current = operation.result;
  state.expression = `${operation.left} ${operatorSymbol(operation.operator)} ${operation.right} =`;
  state.firstOperand = null;
  state.operator = null;
  state.waitingForSecond = true;
  state.draftNotes = { left: "", right: "" };
  updateDisplay();
  showToast("اتحفظت الحسبة في السجل");
}

function renderHistory() {
  historyCount.textContent = state.history.length;
  historyList.innerHTML = "";
  if (!state.history.length) {
    historyList.appendChild(emptyState);
    return;
  }
  state.history.forEach((item) => {
    const article = document.createElement("article");
    article.className = "history-item";
    article.innerHTML = `
      <div class="history-meta"><span>${item.createdAt}</span><strong>نتيجة الحسبة</strong></div>
      <div class="history-equation">
        <button class="number-chip ${item.notes.left ? "has-note" : ""}" data-id="${item.id}" data-side="left" type="button">${item.left}</button>
        <span>${operatorSymbol(item.operator)}</span>
        <button class="number-chip ${item.notes.right ? "has-note" : ""}" data-id="${item.id}" data-side="right" type="button">${item.right}</button>
        <span>=</span>
        <strong class="history-result">${item.result}</strong>
      </div>
      ${item.notes.left || item.notes.right ? `<div class="note-preview">${[
        item.notes.left ? `<span><b>${item.left}</b> ${escapeHtml(item.notes.left)}</span>` : "",
        item.notes.right ? `<span><b>${item.right}</b> ${escapeHtml(item.notes.right)}</span>` : ""
      ].filter(Boolean).join("")}</div>` : ""}
    `;
    historyList.appendChild(article);
  });
}

function escapeHtml(value) {
  const element = document.createElement("span");
  element.textContent = value;
  return element.innerHTML;
}

function openNote(id, side) {
  const item = state.history.find((entry) => entry.id === id);
  if (!item) return;
  state.activeNote = { id, side };
  modalNumber.textContent = side === "left" ? item.left : item.right;
  noteInput.value = item.notes[side] || "";
  noteModal.hidden = false;
  document.body.style.overflow = "hidden";
  requestAnimationFrame(() => noteInput.focus());
}

function openDraftNote() {
  const side = state.firstOperand === null ? "left" : "right";
  state.activeNote = { draft: true, side };
  modalNumber.textContent = state.current;
  noteInput.value = state.draftNotes[side] || "";
  noteModal.hidden = false;
  document.body.style.overflow = "hidden";
  requestAnimationFrame(() => noteInput.focus());
}

function closeNote() {
  noteModal.hidden = true;
  state.activeNote = null;
  document.body.style.overflow = "";
}

function updateDraftNote(value) {
  const side = state.firstOperand === null ? "left" : "right";
  state.draftNotes[side] = value;
  renderDraftLedger();
}

function saveNote() {
  if (!state.activeNote) return;
  if (state.activeNote.draft) {
    state.draftNotes[state.activeNote.side] = noteInput.value.trim();
    closeNote();
    showToast("اتحفظت ملاحظة الرقم");
    return;
  }
  const item = state.history.find((entry) => entry.id === state.activeNote.id);
  if (!item) return;
  item.notes[state.activeNote.side] = noteInput.value.trim();
  saveHistory();
  renderHistory();
  renderDraftLedger();
  closeNote();
  showToast("اتحفظت الملاحظة");
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("show");
  window.clearTimeout(showToast.timeout);
  showToast.timeout = window.setTimeout(() => toast.classList.remove("show"), 2400);
}

document.querySelector(".calculator-keys").addEventListener("click", (event) => {
  const button = event.target.closest("button");
  if (!button) return;
  const { value, action } = button.dataset;
  if (/^\d$/.test(value || "")) inputDigit(value);
  else if (value === ".") inputDecimal();
  else if (["+", "-", "*", "/"].includes(value)) chooseOperator(value);
  else if (action === "equals") completeCalculation();
  else if (action === "clear") resetCalculator();
  else if (action === "backspace") {
    state.current = state.current.length > 1 ? state.current.slice(0, -1) : "0";
    updateDisplay();
  } else if (action === "percent") {
    state.current = formatNumber(Number(state.current) / 100);
    updateDisplay();
  }
});

document.querySelector("#clearAll").addEventListener("click", resetCalculator);
currentNoteInput.addEventListener("input", (event) => updateDraftNote(event.target.value));
draftLedger.addEventListener("click", (event) => {
  const entry = event.target.closest(".draft-entry-editable");
  if (entry) openNote(Number(entry.dataset.id), entry.dataset.side);
});
document.querySelector("#clearHistory").addEventListener("click", () => {
  if (!state.history.length) return;
  state.history = [];
  saveHistory();
  renderHistory();
  showToast("اتمسح سجل العمليات");
});
document.querySelector("#toggleHistory").addEventListener("click", () => {
  document.querySelector("#historyPanel").hidden = false;
  document.querySelector("#appGrid").classList.add("with-history");
});
document.querySelector("#closeHistory").addEventListener("click", () => {
  document.querySelector("#historyPanel").hidden = true;
  document.querySelector("#appGrid").classList.remove("with-history");
});
historyList.addEventListener("click", (event) => {
  const button = event.target.closest(".number-chip");
  if (button) openNote(Number(button.dataset.id), button.dataset.side);
});
document.querySelector("#closeModal").addEventListener("click", closeNote);
document.querySelector("#cancelNote").addEventListener("click", closeNote);
document.querySelector("#saveNote").addEventListener("click", saveNote);
noteModal.addEventListener("click", (event) => {
  if (event.target === noteModal) closeNote();
});
document.addEventListener("keydown", (event) => {
  if (!noteModal.hidden) {
    if (event.key === "Escape") closeNote();
    if ((event.ctrlKey || event.metaKey) && event.key === "Enter") saveNote();
    return;
  }
  if (event.target.matches("input, textarea, [contenteditable='true']")) return;
  if (/^\d$/.test(event.key)) inputDigit(event.key);
  else if (event.key === ".") inputDecimal();
  else if (["+", "-", "*", "/"].includes(event.key)) chooseOperator(event.key);
  else if (event.key === "Enter" || event.key === "=") completeCalculation();
  else if (event.key === "Escape") resetCalculator();
  else if (event.key === "Backspace") {
    state.current = state.current.length > 1 ? state.current.slice(0, -1) : "0";
    updateDisplay();
  }
});

renderHistory();
updateDisplay();
