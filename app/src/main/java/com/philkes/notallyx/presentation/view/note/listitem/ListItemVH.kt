package com.philkes.notallyx.presentation.view.note.listitem

import android.text.TextWatcher
import android.util.TypedValue
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.CompoundButton.OnCheckedChangeListener
import android.widget.TextView.INVISIBLE
import android.widget.TextView.VISIBLE
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.ListItem
import com.philkes.notallyx.databinding.RecyclerListItemBinding
import com.philkes.notallyx.presentation.createListTextWatcherWithHistory
import com.philkes.notallyx.presentation.setOnNextAction
import com.philkes.notallyx.presentation.view.misc.SwipeLayout.SwipeActionsListener
import com.philkes.notallyx.presentation.viewmodel.preference.ListItemSort
import com.philkes.notallyx.presentation.viewmodel.preference.TextSize

class ListItemVH(
    val binding: RecyclerListItemBinding,
    val listManager: ListManager,
    touchHelper: ItemTouchHelper,
    textSize: TextSize,
) : RecyclerView.ViewHolder(binding.root) {

    private var editTextWatcher: TextWatcher
    private var dragHandleInitialY: Float = 0f

    init {
        val body = textSize.editBodySize
        binding.EditText.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, body)

            setOnNextAction {
                val position = adapterPosition + 1
                listManager.add(position)
            }

            editTextWatcher =
                createListTextWatcherWithHistory(listManager, this@ListItemVH::getAdapterPosition)
            addTextChangedListener(editTextWatcher)

            setOnFocusChangeListener { _, hasFocus ->
                binding.Delete.visibility = if (hasFocus) VISIBLE else INVISIBLE
            }
        }

        binding.DragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> dragHandleInitialY = event.y
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_CANCEL -> {
                    val dY = Math.abs(dragHandleInitialY!! - event.y)
                    if (dY > binding.DragHandle.measuredHeight * 0.15f) {
                        touchHelper.startDrag(this)
                    }
                }
            }
            false
        }
    }

    fun bind(item: ListItem, position: Int, autoSort: ListItemSort) {
        updateEditText(item, position)

        updateCheckBox(item, position)

        updateDeleteButton(item, position)

        updateSwipe(item.isChild, position != 0 && !item.checked)
        binding.DragHandle.apply {
            visibility =
                if (item.checked && autoSort == ListItemSort.AUTO_SORT_BY_CHECKED) {
                    INVISIBLE
                } else {
                    VISIBLE
                }
            contentDescription = "Drag$position"
        }
    }

    fun focusEditText(
        selectionStart: Int = binding.EditText.text!!.length,
        inputMethodManager: InputMethodManager,
    ) {
        binding.EditText.apply {
            requestFocus()
            setSelection(selectionStart)
            inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun updateDeleteButton(item: ListItem, position: Int) {
        binding.Delete.apply {
            visibility = if (item.checked) VISIBLE else INVISIBLE
            setOnClickListener { listManager.delete(adapterPosition) }
            contentDescription = "Delete$position"
        }
    }

    private fun updateEditText(item: ListItem, position: Int) {
        binding.EditText.apply {
            removeTextChangedListener(editTextWatcher)
            setText(item.body)
            isEnabled = !item.checked
            addTextChangedListener(editTextWatcher)
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_DEL) {
                    // TODO: when there are multiple checked items above it does not jump to the
                    // last
                    // unchecked item but always re-adds a new item
                    listManager.delete(adapterPosition, false) != null
                } else {
                    false
                }
            }
            contentDescription = "EditText$position"
        }
    }

    private var checkBoxListener: OnCheckedChangeListener? = null

    private fun updateCheckBox(item: ListItem, position: Int) {
        if (checkBoxListener == null) {
            checkBoxListener = OnCheckedChangeListener { buttonView, isChecked ->
                buttonView!!.setOnCheckedChangeListener(null)
                listManager.changeChecked(adapterPosition, isChecked)
                buttonView.setOnCheckedChangeListener(checkBoxListener)
            }
        }
        binding.CheckBox.apply {
            setOnCheckedChangeListener(null)
            isChecked = item.checked
            setOnCheckedChangeListener(checkBoxListener)
            contentDescription = "CheckBox$position"
        }
    }

    private fun updateSwipe(open: Boolean, canSwipe: Boolean) {
        binding.SwipeLayout.apply {
            setOnActionsListener(null)
            val swipeActionListener =
                object : SwipeActionsListener {
                    override fun onOpen(direction: Int, isContinuous: Boolean) {
                        listManager.changeIsChild(adapterPosition, true)
                    }

                    override fun onClose() {
                        listManager.changeIsChild(adapterPosition, false)
                    }
                }
            isEnabledSwipe = canSwipe
            post {
                if (open) {
                    openLeft(false)
                } else {
                    close(false)
                }
                setOnActionsListener(swipeActionListener)
            }
        }
    }
}
