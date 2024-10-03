package com.omgodse.notally.recyclerview

import androidx.recyclerview.widget.SortedList
import com.omgodse.notally.room.ListItem

class ListItemSortedList(callback: Callback<ListItem>) :
    SortedList<ListItem>(ListItem::class.java, callback) {

    override fun updateItemAt(index: Int, item: ListItem?) {
        updateChildStatus(item, index)
        super.updateItemAt(index, item)
    }

    private fun updateChildStatus(item: ListItem?, index: Int) {
        val wasChild = this[index].isChild
        if (!wasChild && item?.isChild == true) {
            updateChildInParent(index, item)
        } else if (wasChild && item?.isChild == false) {
            // Child becomes parent
            separateChildrenFromParent(item)
        }
    }

    override fun add(item: ListItem?): Int {
        val position = super.add(item)
        if (item?.isChild == true) {
            updateChildInParent(position, item)
        }
        return position
    }

    private fun separateChildrenFromParent(item: ListItem) {
        findParent(item)?.let { (_, parent) ->
            val childIndex = parent.children.indexOfFirst { child -> child.id == item.id }
            // If a child becomes a parent it inherits its children below it
            val separatedChildren =
                if (childIndex < parent.children.lastIndex)
                    parent.children.subList(childIndex + 1, parent.children.size)
                else listOf()
            item.children.clear()
            item.children.addAll(separatedChildren)
            while (parent.children.size >= childIndex + 1) {
                parent.children.removeAt(childIndex)
            }
        }
    }

    fun add(item: ListItem, isChild: Boolean?) {
        if (isChild != null) {
            if (item.isChild != isChild) {
                if (!item.isChild && isChild) {
                    item.children.clear()
                }
                item.isChild = isChild
            }
        }
        add(item)
    }

    override fun remove(item: ListItem?): Boolean {
        if (item?.isChild == true) {
            removeChildFromParent(item)
        }
        return super.remove(item)
    }

    override fun removeItemAt(index: Int): ListItem {
        val item = this[index]
        if (item?.isChild == true) {
            removeChildFromParent(item)
        }
        return super.removeItemAt(index)
    }

    private fun removeChildFromParent(item: ListItem) {
        findParent(item)?.let { (_, parent) ->
            val childIndex = parent.children.indexOfFirst { child -> child.id == item.id }
            parent.children.removeAt(childIndex)
        }
    }

    private fun updateChildInParent(position: Int, item: ListItem) {
        var childIndex: Int? = null
        var parentInfo = findParent(item)
        var parent: ListItem? = null
        if (parentInfo == null) {
            val parentPosition = findLastIsNotChild(position - 1)!!
            childIndex = position - parentPosition - 1
            parent = this[parentPosition]
        } else {
            parent = parentInfo.second
            childIndex = parent.children.indexOfFirst { child -> child.id == item.id }
            parent.children.removeAt(childIndex)
        }
        parent!!.children.add(childIndex, item)
        parent.children.addAll(childIndex + 1, item.children)
        item.children.clear()
    }

    //    override fun updateItemAt(position: Int, newItem: ListItem?) {
    //        // Retrieve the existing item to maintain the children
    //        val oldItem = this[position]
    //
    //        // Only transfer children if the new item is not a child itself
    //        if (oldItem.children.isNotEmpty() && newItem?.isChild == false) {
    //            newItem.children.clear() // Clear existing children if necessary
    //            newItem.children.addAll(oldItem.children) // Transfer children
    //        }
    //
    //        // Call the parent method to perform the actual update
    //        super.updateItemAt(position, newItem)
    //    }

    /** @return position of the found item and its difference to index */
    fun findLastIsNotChild(index: Int): Int? {
        var position = index
        while (this[position].isChild) {
            if (position < 0) {
                return null
            }
            position--
        }
        return position
    }

    override fun recalculatePositionOfItemAt(index: Int) {
        super.recalculatePositionOfItemAt(index)
    }
}
