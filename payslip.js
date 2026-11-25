// OT paid by the employer, keyed by the payslip month. Null means not entered; zero is a value.
const PAYSLIPS_KEY = 'garments_ot_payslips_v1';
const MAX_MONTH_MINUTES = 31 * 24 * 60;
const MAX_PAISA = 10000000 * 100;
let monthlyPayslips = {};

function payslipMonth(date) {
    return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0');
}

function asciiDigits(value) {
    return String(value).trim().replace(/[০-৯]/g, char => '০১২৩৪৫৬৭৮৯'.indexOf(char));
}

function readPaidMinutes(value) {
    const s = asciiDigits(value);
    if (!s) return null;
    const clock = /^(\d{1,3}):([0-5]\d)$/.exec(s);
    const decimal = /^(?:\d{1,3}(?:\.\d{1,2})?|\.\d{1,2})$/.test(s);
    const minutes = clock ? Number(clock[1]) * 60 + Number(clock[2])
        : decimal ? Math.round(Number(s) * 60) : NaN;
    if (!Number.isSafeInteger(minutes) || minutes > MAX_MONTH_MINUTES) {
        throw new Error('OT ঘণ্টা ২৪.৫ বা ২৪:৩০ আকারে লিখুন।');
    }
    return minutes;
}

function readPaidPaisa(value) {
    const s = asciiDigits(value);
    if (!s) return null;
    const money = /^(?:\d+|\d{1,3}(?:,\d{3})+|\d{1,2}(?:,\d{2})*,\d{3})(?:\.\d{1,2})?$/;
    const paisa = money.test(s) ? Math.round(Number(s.replace(/,/g, '')) * 100) : NaN;
    if (!Number.isSafeInteger(paisa) || paisa > MAX_PAISA) {
        throw new Error('OT বিল টাকায় লিখুন। যেমন ১,২৫০ বা ১২৫০.৫০।');
    }
    return paisa;
}

function validPayslips(raw) {
    const result = {};
    if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return result;
    for (const [month, item] of Object.entries(raw)) {
        if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month) || !item || typeof item !== 'object') continue;
        const {paidMinutes, paidPaisa} = item;
        const valid = (n, max) => n === null || (Number.isSafeInteger(n) && n >= 0 && n <= max);
        if (valid(paidMinutes, MAX_MONTH_MINUTES) && valid(paidPaisa, MAX_PAISA)
                && (paidMinutes !== null || paidPaisa !== null)) {
            result[month] = {paidMinutes, paidPaisa};
        }
    }
    return result;
}

function hoursAndMinutes(minutes) {
    const hours = Math.floor(minutes / 60);
    const rest = minutes % 60;
    if (!rest) return toBnNum(hours) + ' ঘণ্টা';
    return (hours ? toBnNum(hours) + ' ঘণ্টা ' : '') + toBnNum(rest) + ' মিনিট';
}

function payslipAmount(paisa) {
    return '৳' + toBnNum(paisa / 100);
}

function payslipDifference(delta, format, matched) {
    if (delta === 0) return matched;
    return 'পে-স্লিপে ' + format(Math.abs(delta)) + (delta > 0 ? ' কম' : ' বেশি');
}

function payslipLines(stats, record) {
    if (!record) return [];
    const lines = [];
    if (record.paidMinutes !== null) {
        const recorded = Math.round(stats.totalOt * 60);
        lines.push(['লেখা OT', hoursAndMinutes(recorded)]);
        lines.push(['পে-স্লিপের OT', hoursAndMinutes(record.paidMinutes)]);
        lines.push(['ঘণ্টার মিল', payslipDifference(recorded - record.paidMinutes, hoursAndMinutes, 'ঘণ্টা মিলেছে')]);
    }
    if (record.paidPaisa !== null) {
        const expected = stats.otBill * 100;
        lines.push(['আপনার রেটে OT বিল', payslipAmount(expected)]);
        lines.push(['পে-স্লিপের OT বিল', payslipAmount(record.paidPaisa)]);
        lines.push(['টাকার মিল', payslipDifference(expected - record.paidPaisa, payslipAmount, 'টাকা মিলেছে')]);
    }
    return lines;
}

function renderPayslipCheck(stats) {
    const month = payslipMonth(activeMonthDate);
    const record = monthlyPayslips[month];
    const form = document.getElementById('paidOtForm');
    const identity = month + JSON.stringify(record || null);
    if (form.dataset.saved !== identity) {
        const minutes = record && record.paidMinutes;
        let hours = '';
        if (minutes != null) {
            hours = minutes % 60 ? Math.floor(minutes / 60) + ':' + String(minutes % 60).padStart(2, '0') : String(minutes / 60);
        }
        document.getElementById('paidOtHours').value = toBnNum(hours);
        document.getElementById('paidOtMoney').value = record && record.paidPaisa != null ? toBnNum(record.paidPaisa / 100) : '';
        document.getElementById('paidOtError').textContent = '';
        form.dataset.saved = identity;
    }
    document.getElementById('otCheckMonth').textContent = BN_MONTHS[activeMonthDate.getMonth()] + ' ' + toBnNum(activeMonthDate.getFullYear());
    document.getElementById('otCheckRecorded').textContent = hoursAndMinutes(Math.round(stats.totalOt * 60));
    document.getElementById('otCheckExpected').textContent = payslipAmount(stats.otBill * 100);
    document.getElementById('clearPaidOt').hidden = !record;
    const output = document.getElementById('paidOtResult');
    output.textContent = '';
    if (!record) {
        output.textContent = 'এই মাসের পে-স্লিপের তথ্য এখনও দেওয়া হয়নি।';
        return;
    }
    for (const [label, value] of payslipLines(stats, record)) {
        if (label !== 'ঘণ্টার মিল' && label !== 'টাকার মিল') continue;
        const line = document.createElement('p');
        line.textContent = value;
        output.appendChild(line);
    }
}

function commitPayslip(month, record) {
    const next = {...monthlyPayslips};
    if (record) next[month] = record;
    else delete next[month];
    try {
        localStorage.setItem(PAYSLIPS_KEY, JSON.stringify(next));
    } catch (error) {
        showToast('তথ্য সেভ হয়নি। ফোনে খালি জায়গা আছে কি না দেখুন।');
        return false;
    }
    monthlyPayslips = next;
    document.getElementById('paidOtForm').dataset.saved = '';
    renderAll();
    return true;
}

function savePayslipCheck(event) {
    event.preventDefault();
    const errorLine = document.getElementById('paidOtError');
    try {
        const paidMinutes = readPaidMinutes(document.getElementById('paidOtHours').value);
        const paidPaisa = readPaidPaisa(document.getElementById('paidOtMoney').value);
        if (paidMinutes === null && paidPaisa === null) {
            throw new Error('পে-স্লিপ থেকে OT ঘণ্টা বা OT বিল লিখুন। শূন্য হলে ০ লিখুন।');
        }
        if (commitPayslip(payslipMonth(activeMonthDate), {paidMinutes, paidPaisa})) {
            showToast('এই মাসের পে-স্লিপ সেভ হয়েছে');
        }
    } catch (error) {
        errorLine.textContent = error.message;
    }
}

function clearPayslipCheck() {
    const month = payslipMonth(activeMonthDate);
    openConfirmModal('পে-স্লিপের তথ্য মুছবেন?', 'নির্বাচিত মাসের পে-স্লিপ থেকে লেখা ঘণ্টা ও বিল মুছে যাবে।', () => {
        if (commitPayslip(month, null)) showToast('এই মাসের পে-স্লিপের তথ্য মুছে গেছে');
    });
}
