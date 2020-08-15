package com.sergsave.purryourcat.fragments

import androidx.fragment.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.fragment_toolbar.*
import com.sergsave.purryourcat.R
import com.sergsave.purryourcat.helpers.setToolbarAsActionBar

open class ToolbarFragment : Fragment() {

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_toolbar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.title = title
        val compatActivity = activity as? AppCompatActivity
        compatActivity?.setToolbarAsActionBar(toolbar, isBackEnabled)
    }

    open protected val title: String?
        get() {
            return arguments?.let{ it.getString(ARG_TITLE) }
        }

    open protected val isBackEnabled: Boolean
        get() {
            return arguments?.let{ it.getBoolean(ARG_IS_BACK_ENABLED) } ?: false
        }

    companion object {
        private val ARG_TITLE = "ArgTitle"
        private val ARG_IS_BACK_ENABLED = "ArgIsBackEnabled"

        @JvmStatic
        fun newInstance(title: String?, isBackEnabled: Boolean = true) =
            ToolbarFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putBoolean(ARG_IS_BACK_ENABLED, isBackEnabled)
                }
            }
    }
}